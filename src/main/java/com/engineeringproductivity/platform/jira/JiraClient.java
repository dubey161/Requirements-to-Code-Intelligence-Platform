package com.engineeringproductivity.platform.jira;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Thin REST client for Jira Cloud API v3.
 * Uses Basic auth: base64(email:api_token).
 */
@Component
public class JiraClient {

    private final JiraProperties props;
    private final RestClient restClient;

    public JiraClient(JiraProperties props) {
        this.props = props;
        this.restClient = RestClient.builder()
                .baseUrl(props.baseUrl())
                .defaultHeader("Authorization", basicAuth(props.email(), props.apiToken()))
                .defaultHeader("Accept", "application/json")
                .build();
    }

    public JiraIssue fetchIssue(String issueKey) {
        String fieldsParam = "summary,description," + props.acceptanceCriteriaField();
        return restClient.get()
                .uri("/rest/api/3/issue/{key}?fields=" + fieldsParam, issueKey)
                .retrieve()
                .body(JiraIssue.class);
    }

    private String basicAuth(String email, String token) {
        String credentials = email + ":" + token;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Jira REST API response DTOs
    // ──────────────────────────────────────────────────────────────────────────

    public record JiraIssue(String id, String key, Fields fields) {}

    public record Fields(
            String summary,
            DescriptionDocument description,
            Object customfield_10016  // acceptance criteria — varies by Jira config
    ) {}

    /**
     * Jira uses Atlassian Document Format (ADF) for rich text.
     * We extract plain text from it recursively.
     */
    public record DescriptionDocument(String type, List<Map<String, Object>> content) {}
}
