package com.engineeringproductivity.platform.aireview.application;

import com.engineeringproductivity.platform.aireview.domain.AiPrReview;
import com.engineeringproductivity.platform.aireview.domain.AiPrReviewRepository;
import com.engineeringproductivity.platform.aireview.domain.AiReviewIssue;
import com.engineeringproductivity.platform.common.api.ResourceNotFoundException;
import com.engineeringproductivity.platform.github.GitHubClient;
import com.engineeringproductivity.platform.github.domain.PullRequestRepository;
import com.engineeringproductivity.platform.requirement.analysis.application.RequirementAnalyzerService;
import com.engineeringproductivity.platform.requirement.analysis.domain.model.RequirementKnowledgeModel;
import com.engineeringproductivity.platform.requirement.analysis.application.gateway.LlmGateway;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Sends the requirement + full PR diff to the LLM and asks it to review
 * for security, validation, architecture, performance, and requirement gaps.
 *
 * Every review is persisted with the model ID and prompt version —
 * enabling audit trails as models are upgraded.
 */
@Service
public class AiPrReviewService {

    private static final Logger log = LoggerFactory.getLogger(AiPrReviewService.class);
    private static final String PROMPT_VERSION = "v1";

    private static final String SYSTEM_PROMPT = """
            You are a senior software engineer conducting a code review.
            You will receive:
            1. A software requirement (entities, endpoints, validations, security requirements)
            2. The pull request diff

            Review the PR thoroughly for:
            - SECURITY: injection vulnerabilities, missing authentication, unencrypted sensitive fields, OWASP Top 10
            - VALIDATION: fields that should be validated but aren't, missing @NotBlank/@Email/@Size
            - CODE_SMELL: overly complex methods, missing error handling, hardcoded values, magic strings
            - ARCHITECTURE: wrong layer responsibility, missing abstractions, tight coupling
            - PERFORMANCE: N+1 queries, missing indexes, synchronous I/O where async is needed
            - REQUIREMENT_GAP: things in the requirement not implemented in the PR

            Return ONLY valid JSON. No markdown. No explanation. Exact schema:
            {
              "summary": "one paragraph overall review",
              "approved": false,
              "issues": [
                {
                  "category": "SECURITY",
                  "severity": "CRITICAL",
                  "description": "what the issue is",
                  "suggestion": "how to fix it",
                  "location": "ClassName.methodName or filename"
                }
              ]
            }
            """;

    private final RequirementAnalyzerService analyzerService;
    private final PullRequestRepository prRepository;
    private final GitHubClient gitHubClient;
    private final Optional<LlmGateway> llmGateway;
    private final AiPrReviewRepository reviewRepository;
    private final ObjectMapper objectMapper;

    public AiPrReviewService(RequirementAnalyzerService analyzerService,
                              PullRequestRepository prRepository,
                              GitHubClient gitHubClient,
                              Optional<LlmGateway> llmGateway,
                              AiPrReviewRepository reviewRepository,
                              ObjectMapper objectMapper) {
        this.analyzerService = analyzerService;
        this.prRepository = prRepository;
        this.gitHubClient = gitHubClient;
        this.llmGateway = llmGateway;
        this.reviewRepository = reviewRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AiPrReview review(UUID requirementId) {
        RequirementKnowledgeModel model = analyzerService.getAnalysis(requirementId).getKnowledgeModel();

        var pr = prRepository.findByRequirementId(requirementId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No PR found for requirement: " + requirementId));

        String diff = gitHubClient.getPrDiff(pr.getPrNumber());
        log.info("Running AI PR review for requirement={}, pr=#{}, diff_length={}",
                requirementId, pr.getPrNumber(), diff.length());

        LlmGateway gateway = llmGateway.orElseThrow(() ->
                new IllegalStateException("AI review requires an LLM provider. " +
                        "Set ANALYZER_PROVIDER=gemini, groq, or ollama."));

        String userPrompt = buildUserPrompt(model, diff);
        String rawResponse = gateway.complete(SYSTEM_PROMPT, userPrompt);

        ParsedReview parsed = parseResponse(rawResponse);

        AiPrReview review = AiPrReview.create(
                requirementId, pr.getPrNumber(),
                gateway.modelId(), PROMPT_VERSION,
                parsed.summary(), parsed.approved(),
                parsed.issues(), rawResponse
        );

        AiPrReview saved = reviewRepository.save(review);

        // Post review summary as PR comment
        gitHubClient.createReviewComment(pr.getPrNumber(),
                formatPrComment(parsed));

        log.info("AI review complete: issues={}, approved={}", parsed.issues().size(), parsed.approved());
        return saved;
    }

    @Transactional(readOnly = true)
    public AiPrReview getLatestReview(UUID requirementId) {
        return reviewRepository.findTopByRequirementIdOrderByReviewedAtDesc(requirementId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No AI review for requirement: " + requirementId +
                        ". Trigger via POST /api/v1/requirements/{id}/ai-review"));
    }

    @Transactional(readOnly = true)
    public List<AiPrReview> getReviewHistory(UUID requirementId) {
        return reviewRepository.findAllByRequirementIdOrderByReviewedAtDesc(requirementId);
    }

    private String buildUserPrompt(RequirementKnowledgeModel model, String diff) {
        return """
                === REQUIREMENT ===
                Entities: %s
                API Endpoints: %s
                Validation Rules: %s
                Security Requirements: %s
                Functional Requirements: %s

                === PULL REQUEST DIFF ===
                %s

                Review this PR against the requirement. Return JSON only.
                """.formatted(
                model.entities().stream().map(e -> e.name()).toList(),
                model.apiEndpoints().stream().map(e -> e.httpMethod() + " " + e.suggestedPath()).toList(),
                model.validationRules().stream().map(v -> v.targetField() + ": " + v.ruleType()).toList(),
                model.securityRequirements().stream().map(s -> s.category() + ": " + s.description()).toList(),
                model.functionalRequirements().stream().map(f -> f.id() + ": " + f.description()).toList(),
                diff.length() > 8000 ? diff.substring(0, 8000) + "\n[diff truncated]" : diff
        );
    }

    private ParsedReview parseResponse(String raw) {
        try {
            String json = extractJson(raw);
            JsonNode root = objectMapper.readTree(json);

            String summary = root.path("summary").asText("No summary provided");
            boolean approved = root.path("approved").asBoolean(false);

            List<AiReviewIssue> issues = new ArrayList<>();
            JsonNode issuesNode = root.path("issues");
            if (issuesNode.isArray()) {
                for (JsonNode issue : issuesNode) {
                    issues.add(new AiReviewIssue(
                            issue.path("category").asText("UNKNOWN"),
                            issue.path("severity").asText("MEDIUM"),
                            issue.path("description").asText(""),
                            issue.path("suggestion").asText(""),
                            issue.path("location").asText("")
                    ));
                }
            }
            return new ParsedReview(summary, approved, issues);
        } catch (Exception e) {
            log.error("Failed to parse AI review response: {}", raw, e);
            return new ParsedReview("AI review parsing failed: " + e.getMessage(), false, List.of());
        }
    }

    private String extractJson(String raw) {
        if (raw == null || raw.isBlank()) return "{}";
        String trimmed = raw.strip();
        if (trimmed.startsWith("```")) {
            int nl = trimmed.indexOf('\n');
            int end = trimmed.lastIndexOf("```");
            if (nl > 0 && end > nl) trimmed = trimmed.substring(nl + 1, end).strip();
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        return (start >= 0 && end > start) ? trimmed.substring(start, end + 1) : "{}";
    }

    private String formatPrComment(ParsedReview review) {
        String emoji = review.approved() ? "✅" : "❌";
        long critical = review.issues().stream().filter(i -> "CRITICAL".equals(i.severity())).count();
        long high = review.issues().stream().filter(i -> "HIGH".equals(i.severity())).count();

        StringBuilder sb = new StringBuilder();
        sb.append("## %s AI Code Review — %s\n\n".formatted(emoji, review.approved() ? "APPROVED" : "CHANGES REQUESTED"));
        sb.append("> ").append(review.summary()).append("\n\n");
        sb.append("**Issues found:** %d total · %d critical · %d high\n\n".formatted(
                review.issues().size(), critical, high));

        if (!review.issues().isEmpty()) {
            sb.append("| Severity | Category | Description | Suggestion |\n");
            sb.append("|---|---|---|---|\n");
            review.issues().forEach(issue ->
                    sb.append("| **%s** | %s | %s | %s |\n".formatted(
                            issue.severity(), issue.category(), issue.description(), issue.suggestion())));
        }
        sb.append("\n*Model: ").append(llmGateway.map(LlmGateway::modelId).orElse("unknown")).append(" · Prompt: ").append(PROMPT_VERSION).append("*");
        return sb.toString();
    }

    private record ParsedReview(String summary, boolean approved, List<AiReviewIssue> issues) {}
}
