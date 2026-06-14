package com.engineeringproductivity.platform.github.webhook;

import com.engineeringproductivity.platform.compliance.application.ComplianceService;
import com.engineeringproductivity.platform.github.domain.PullRequestRepository;
import com.engineeringproductivity.platform.risk.application.RiskScoringService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Dispatches GitHub webhook events to the appropriate pipeline stages.
 *
 * pull_request.opened / pull_request.synchronize
 *   → compliance check → risk score
 *
 * pull_request.closed (merged)
 *   → log final state
 *
 * pull_request_review.submitted
 *   → log reviewer decision
 */
@Component
public class GitHubWebhookHandler {

    private static final Logger log = LoggerFactory.getLogger(GitHubWebhookHandler.class);

    private final ObjectMapper objectMapper;
    private final PullRequestRepository prRepository;
    private final ComplianceService complianceService;
    private final RiskScoringService riskScoringService;

    public GitHubWebhookHandler(ObjectMapper objectMapper,
                                 PullRequestRepository prRepository,
                                 ComplianceService complianceService,
                                 RiskScoringService riskScoringService) {
        this.objectMapper = objectMapper;
        this.prRepository = prRepository;
        this.complianceService = complianceService;
        this.riskScoringService = riskScoringService;
    }

    @Async("pipelineExecutor")
    public void handle(String event, String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            switch (event) {
                case "pull_request" -> handlePullRequest(root);
                case "pull_request_review" -> handlePullRequestReview(root);
                default -> log.debug("Unhandled webhook event: {}", event);
            }
        } catch (Exception e) {
            log.error("Failed to handle webhook event: {}", event, e);
        }
    }

    private void handlePullRequest(JsonNode root) {
        String action = root.path("action").asText();
        int prNumber = root.path("pull_request").path("number").asInt();
        String prTitle = root.path("pull_request").path("title").asText();

        log.info("pull_request event: action={}, pr=#{}, title={}", action, prNumber, prTitle);

        if ("opened".equals(action) || "synchronize".equals(action)) {
            triggerComplianceAndRisk(prNumber, action);
        } else if ("closed".equals(action)) {
            boolean merged = root.path("pull_request").path("merged").asBoolean();
            log.info("PR #{} {} {}", prNumber, merged ? "merged" : "closed without merge", prTitle);
        }
    }

    private void handlePullRequestReview(JsonNode root) {
        String state = root.path("review").path("state").asText();
        int prNumber = root.path("pull_request").path("number").asInt();
        String reviewer = root.path("review").path("user").path("login").asText();
        log.info("PR review: pr=#{}, state={}, reviewer={}", prNumber, state, reviewer);
    }

    private void triggerComplianceAndRisk(int prNumber, String trigger) {
        prRepository.findAll().stream()
                .filter(pr -> pr.getPrNumber() == prNumber)
                .findFirst()
                .ifPresentOrElse(pr -> {
                    log.info("Webhook {} triggered compliance+risk for requirement={}, pr=#{}",
                            trigger, pr.getRequirementId(), prNumber);
                    try {
                        complianceService.check(pr.getRequirementId());
                        riskScoringService.score(pr.getRequirementId());
                    } catch (Exception e) {
                        log.error("Webhook-triggered pipeline failed for pr=#{}", prNumber, e);
                    }
                }, () -> log.warn("No requirement found for PR #{} — not tracked by this platform", prNumber));
    }
}
