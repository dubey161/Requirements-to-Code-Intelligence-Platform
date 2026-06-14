package com.engineeringproductivity.platform.jira;

import com.engineeringproductivity.platform.requirement.application.RequirementService;
import com.engineeringproductivity.platform.requirement.domain.RequirementStory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Maps a Jira issue to a RequirementStory and persists it.
 * Handles Atlassian Document Format (ADF) → plain text conversion.
 */
@Service
public class JiraIssueImporter {

    private static final Logger log = LoggerFactory.getLogger(JiraIssueImporter.class);

    private final JiraClient jiraClient;
    private final RequirementService requirementService;

    public JiraIssueImporter(JiraClient jiraClient, RequirementService requirementService) {
        this.jiraClient = jiraClient;
        this.requirementService = requirementService;
    }

    public RequirementStory importIssue(String issueKey, UUID createdBy) {
        log.info("Importing Jira issue: {}", issueKey);

        JiraClient.JiraIssue issue = jiraClient.fetchIssue(issueKey);

        String title = issue.fields().summary();
        String description = extractDescription(issue.fields().description());
        List<String> acceptanceCriteria = extractAcceptanceCriteria(issue.fields().customfield_10016());

        // customfield_10016 is Story Points (a Number) in most Jira Cloud instances.
        // When it yields nothing useful, promote description sentences to acceptance criteria
        // so the analyzer has structured constraints to work with.
        if (acceptanceCriteria.isEmpty() && !description.isBlank()) {
            acceptanceCriteria = extractSentencesAsAC(description);
            log.info("AC field empty/numeric — promoted {} sentences from description as acceptance criteria", acceptanceCriteria.size());
        }

        log.info("Importing {}: title='{}', desc='{}', {} acceptance criteria",
                issueKey, title, description, acceptanceCriteria.size());

        return requirementService.create(issueKey, title, description, acceptanceCriteria, createdBy);
    }

    /**
     * Splits description text into individual sentences/lines and returns them as acceptance criteria.
     * Skips very short fragments. Useful when the Jira AC custom field holds story points instead.
     */
    private List<String> extractSentencesAsAC(String description) {
        List<String> result = new ArrayList<>();
        // Split on sentence boundaries and bullet separators
        for (String sentence : description.split("[.!?\\n•]")) {
            String trimmed = sentence.trim();
            if (trimmed.length() > 10) {  // ignore tiny fragments
                result.add(trimmed);
            }
        }
        return result;
    }

    // ── ADF → plain text ──────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private String extractDescription(JiraClient.DescriptionDocument doc) {
        if (doc == null) return "";
        return extractAdfText(doc.content());
    }

    /**
     * Jira acceptance criteria may be stored as:
     * 1. ADF document (same as description)
     * 2. Plain string
     * 3. List of strings
     * 4. null
     */
    @SuppressWarnings("unchecked")
    private List<String> extractAcceptanceCriteria(Object raw) {
        List<String> result = new ArrayList<>();
        if (raw == null) return result;

        if (raw instanceof String s) {
            // Plain text — split by newline or bullet
            for (String line : s.split("[\\n•\\-\\*]")) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) result.add(trimmed);
            }
            return result;
        }

        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof String s) result.add(s.trim());
                else if (item instanceof Map<?, ?> map) {
                    Object text = map.get("text");
                    if (text instanceof String s) result.add(s.trim());
                }
            }
            return result;
        }

        if (raw instanceof Map<?, ?> map) {
            // ADF document
            Object content = map.get("content");
            if (content instanceof List<?> contentList) {
                String text = extractAdfText((List<Map<String, Object>>) contentList);
                for (String line : text.split("[\\n•]")) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty()) result.add(trimmed);
                }
            }
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    private String extractAdfText(List<Map<String, Object>> nodes) {
        if (nodes == null) return "";
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> node : nodes) {
            String type = (String) node.get("type");
            if ("text".equals(type)) {
                Object text = node.get("text");
                if (text != null) sb.append(text);
            } else if ("hardBreak".equals(type)) {
                sb.append("\n");
            } else if ("paragraph".equals(type) || "listItem".equals(type)) {
                // Process children first, then add newline separator
                Object children = node.get("content");
                if (children instanceof List<?> childList) {
                    String childText = extractAdfText((List<Map<String, Object>>) childList);
                    if (!childText.isBlank()) {
                        sb.append(childText).append("\n");
                    }
                }
                continue; // skip the generic children processing below
            }
            Object children = node.get("content");
            if (children instanceof List<?> childList) {
                sb.append(extractAdfText((List<Map<String, Object>>) childList));
            }
        }
        return sb.toString().trim();
    }
}
