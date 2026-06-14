package com.engineeringproductivity.platform.github;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * GitHub REST API v3 client.
 * Handles: branch creation, file commits (Contents API), PR creation, diff fetching.
 */
@Component
public class GitHubClient {

    private static final Logger log = LoggerFactory.getLogger(GitHubClient.class);
    private static final String BASE_URL = "https://api.github.com";

    private final GitHubProperties props;
    private final RestClient restClient;

    public GitHubClient(GitHubProperties props) {
        this.props = props;
        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("Authorization", "Bearer " + props.token())
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();
    }

    /** Gets the SHA of the latest commit on the base branch. */
    public String getBaseBranchSha() {
        @SuppressWarnings("unchecked")
        Map<String, Object> ref = restClient.get()
                .uri("/repos/{owner}/{repo}/git/refs/heads/{branch}",
                        props.owner(), props.repo(), props.baseBranch())
                .retrieve()
                .body(Map.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> object = (Map<String, Object>) ref.get("object");
        return (String) object.get("sha");
    }

    /** Creates a new branch pointing at baseSha. Returns the new branch ref. */
    public void createBranch(String branchName, String baseSha) {
        try {
            restClient.post()
                    .uri("/repos/{owner}/{repo}/git/refs", props.owner(), props.repo())
                    .body(Map.of("ref", "refs/heads/" + branchName, "sha", baseSha))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Created branch: {}", branchName);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNPROCESSABLE_ENTITY) {
                log.warn("Branch {} already exists, continuing", branchName);
            } else {
                throw e;
            }
        }
    }

    /**
     * Creates or updates a file in the repository.
     * Returns the commit SHA.
     */
    public String upsertFile(String branchName, String filePath, String content, String message) {
        String encoded = Base64.getEncoder().encodeToString(content.getBytes());

        // Check if file exists to get its SHA (needed for update)
        String existingSha = getFileSha(branchName, filePath);

        Map<String, Object> body = existingSha != null
                ? Map.of("message", message, "content", encoded, "branch", branchName, "sha", existingSha)
                : Map.of("message", message, "content", encoded, "branch", branchName);

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient.put()
                .uri("/repos/{owner}/{repo}/contents/{path}",
                        props.owner(), props.repo(), filePath)
                .body(body)
                .retrieve()
                .body(Map.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> commit = (Map<String, Object>) response.get("commit");
        return (String) commit.get("sha");
    }

    /** Creates a pull request. Returns the PR number and URL. */
    public CreatedPr createPullRequest(String title, String body, String headBranch) {
        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient.post()
                .uri("/repos/{owner}/{repo}/pulls", props.owner(), props.repo())
                .body(Map.of(
                        "title", title,
                        "body", body,
                        "head", headBranch,
                        "base", props.baseBranch()
                ))
                .retrieve()
                .body(Map.class);

        int number = ((Number) response.get("number")).intValue();
        String htmlUrl = (String) response.get("html_url");
        String nodeSha = (String) ((Map<?, ?>) response.get("head")).get("sha");
        log.info("Created PR #{}: {}", number, htmlUrl);
        return new CreatedPr(number, htmlUrl, headBranch, nodeSha);
    }

    /** Fetches the diff of a PR as plain text. */
    public String getPrDiff(int prNumber) {
        return restClient.get()
                .uri("/repos/{owner}/{repo}/pulls/{number}",
                        props.owner(), props.repo(), prNumber)
                .header("Accept", "application/vnd.github.diff")
                .retrieve()
                .body(String.class);
    }

    /** Lists changed files in a PR. */
    @SuppressWarnings("unchecked")
    public List<PrFile> getPrFiles(int prNumber) {
        List<Map<String, Object>> raw = restClient.get()
                .uri("/repos/{owner}/{repo}/pulls/{number}/files",
                        props.owner(), props.repo(), prNumber)
                .retrieve()
                .body(List.class);

        if (raw == null) return List.of();
        return raw.stream()
                .map(m -> new PrFile(
                        (String) m.get("filename"),
                        (String) m.get("status"),
                        ((Number) m.getOrDefault("additions", 0)).intValue(),
                        ((Number) m.getOrDefault("deletions", 0)).intValue(),
                        (String) m.getOrDefault("patch", "")
                ))
                .toList();
    }

    /** Posts a review comment on a PR. */
    public void createReviewComment(int prNumber, String body) {
        restClient.post()
                .uri("/repos/{owner}/{repo}/issues/{number}/comments",
                        props.owner(), props.repo(), prNumber)
                .body(Map.of("body", body))
                .retrieve()
                .toBodilessEntity();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String getFileSha(String branch, String path) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> file = restClient.get()
                    .uri("/repos/{owner}/{repo}/contents/{path}?ref={branch}",
                            props.owner(), props.repo(), path, branch)
                    .retrieve()
                    .body(Map.class);
            return file != null ? (String) file.get("sha") : null;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) return null;
            throw e;
        }
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    public record CreatedPr(int number, String htmlUrl, String headBranch, String headSha) {}

    public record PrFile(String filename, String status, int additions, int deletions, String patch) {}
}
