package com.engineeringproductivity.platform.externalreview;

import com.engineeringproductivity.platform.aireview.domain.AiReviewIssue;
import com.engineeringproductivity.platform.externalreview.ExternalReviewController.ExternalReviewResponse;
import com.engineeringproductivity.platform.github.GitHubClient;
import com.engineeringproductivity.platform.jira.JiraClient;
import com.engineeringproductivity.platform.requirement.analysis.application.gateway.LlmGateway;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reviews any GitHub PR against any Jira requirement — no pipeline state needed.
 * Useful for reviewing external/pre-existing PRs against new or existing Jira issues.
 */
@Service
public class ExternalReviewService {

    private static final Logger log = LoggerFactory.getLogger(ExternalReviewService.class);

    private static final String SYSTEM_PROMPT = """
            You are a senior software engineer conducting a code review.
            You will receive a Jira requirement and a GitHub PR diff.

            Review the PR for:
            - SECURITY: injection, missing auth, unencrypted sensitive fields, OWASP Top 10
            - VALIDATION: fields missing @NotBlank/@Email/@Size, unsanitised inputs
            - CODE_SMELL: complex methods, hardcoded values, missing error handling
            - ARCHITECTURE: wrong layer responsibility, missing abstractions, tight coupling
            - PERFORMANCE: N+1 queries, missing indexes
            - REQUIREMENT_GAP: things in the Jira requirement not implemented in the PR

            Return ONLY valid JSON — no markdown, no explanation:
            {
              "summary": "one paragraph overall verdict",
              "approved": false,
              "issues": [
                {
                  "category": "SECURITY",
                  "severity": "CRITICAL",
                  "description": "what the issue is",
                  "suggestion": "how to fix it",
                  "location": "ClassName.method or filename"
                }
              ]
            }
            """;

    private final JiraClient jiraClient;
    private final GitHubClient gitHubClient;
    private final Optional<LlmGateway> llmGateway;
    private final ObjectMapper objectMapper;

    public ExternalReviewService(JiraClient jiraClient,
                                  GitHubClient gitHubClient,
                                  Optional<LlmGateway> llmGateway,
                                  ObjectMapper objectMapper) {
        this.jiraClient = jiraClient;
        this.gitHubClient = gitHubClient;
        this.llmGateway = llmGateway;
        this.objectMapper = objectMapper;
    }

    public Result review(String jiraKey, int prNumber) {
        LlmGateway gateway = llmGateway.orElseThrow(() ->
                new IllegalStateException("AI review requires an LLM provider. " +
                        "Set ANALYZER_PROVIDER=gemini, groq, or ollama in your .env"));

        log.info("External review: jiraKey={}, prNumber={}", jiraKey, prNumber);

        JiraClient.JiraIssue issue = jiraClient.fetchIssue(jiraKey);
        String title = issue.fields().summary();
        String description = extractDescription(issue.fields().description());

        String diff = gitHubClient.getPrDiff(prNumber);
        log.info("Fetched diff for PR #{}: {} chars", prNumber, diff.length());

        String userPrompt = """
                === JIRA REQUIREMENT: %s ===
                Title: %s
                Description: %s

                === PULL REQUEST DIFF (PR #%d) ===
                %s

                Review this PR against the Jira requirement. Return JSON only.
                """.formatted(
                jiraKey, title, description,
                prNumber,
                diff.length() > 10000 ? diff.substring(0, 10000) + "\n[diff truncated]" : diff
        );

        String raw = gateway.complete(SYSTEM_PROMPT, userPrompt);
        log.info("LLM review complete for {}", jiraKey);

        Parsed parsed = parse(raw);
        return new Result(jiraKey, prNumber, title, description, gateway.modelId(), parsed);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Parsed parse(String raw) {
        try {
            String json = extractJson(raw);
            JsonNode root = objectMapper.readTree(json);
            String summary = root.path("summary").asText("No summary");
            boolean approved = root.path("approved").asBoolean(false);
            List<AiReviewIssue> issues = new ArrayList<>();
            JsonNode arr = root.path("issues");
            if (arr.isArray()) {
                for (JsonNode n : arr) {
                    issues.add(new AiReviewIssue(
                            n.path("category").asText("UNKNOWN"),
                            n.path("severity").asText("MEDIUM"),
                            n.path("description").asText(""),
                            n.path("suggestion").asText(""),
                            n.path("location").asText("")
                    ));
                }
            }
            return new Parsed(summary, approved, issues);
        } catch (Exception e) {
            log.error("Failed to parse review response", e);
            return new Parsed("Parsing failed: " + e.getMessage(), false, List.of());
        }
    }

    private String extractJson(String raw) {
        if (raw == null || raw.isBlank()) return "{}";
        String t = raw.strip();
        if (t.startsWith("```")) {
            int nl = t.indexOf('\n');
            int end = t.lastIndexOf("```");
            if (nl > 0 && end > nl) t = t.substring(nl + 1, end).strip();
        }
        int s = t.indexOf('{'), e = t.lastIndexOf('}');
        return (s >= 0 && e > s) ? t.substring(s, e + 1) : "{}";
    }

    @SuppressWarnings("unchecked")
    private String extractDescription(JiraClient.DescriptionDocument doc) {
        if (doc == null || doc.content() == null) return "";
        return extractAdfText(doc.content());
    }

    @SuppressWarnings("unchecked")
    private String extractAdfText(java.util.List<java.util.Map<String, Object>> nodes) {
        if (nodes == null) return "";
        StringBuilder sb = new StringBuilder();
        for (java.util.Map<String, Object> node : nodes) {
            String type = (String) node.get("type");
            if ("text".equals(type)) {
                Object text = node.get("text");
                if (text != null) sb.append(text);
            } else if ("hardBreak".equals(type) || "paragraph".equals(type)) {
                sb.append(" ");
            }
            Object children = node.get("content");
            if (children instanceof java.util.List<?> childList) {
                sb.append(extractAdfText((java.util.List<java.util.Map<String, Object>>) childList));
            }
        }
        return sb.toString().trim();
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    public record Parsed(String summary, boolean approved, List<AiReviewIssue> issues) {}

    public record Result(String jiraKey, int prNumber, String jiraTitle, String jiraDescription,
                         String modelId, Parsed parsed) {
        static ExternalReviewResponse toResponse(Result r) {
            long critical = r.parsed().issues().stream()
                    .filter(i -> "CRITICAL".equals(i.severity())).count();
            return new ExternalReviewResponse(
                    r.jiraKey(), r.prNumber(), r.jiraTitle(), r.jiraDescription(),
                    r.modelId(), r.parsed().summary(), r.parsed().approved(),
                    r.parsed().issues().size(), (int) critical,
                    r.parsed().issues(), Instant.now()
            );
        }
    }
}
