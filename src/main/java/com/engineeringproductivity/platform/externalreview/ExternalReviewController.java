package com.engineeringproductivity.platform.externalreview;

import com.engineeringproductivity.platform.aireview.domain.AiReviewIssue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * Stateless PR review endpoint — no RequirementStory needed in DB.
 * Takes any Jira key + GitHub PR number, fetches both, runs AI review.
 */
@RestController
@RequestMapping("/api/v1/external-review")
public class ExternalReviewController {

    private final ExternalReviewService service;

    public ExternalReviewController(ExternalReviewService service) {
        this.service = service;
    }

    @PostMapping
    public ExternalReviewResponse review(@RequestBody ExternalReviewRequest request) {
        return ExternalReviewService.Result.toResponse(
                service.review(request.jiraKey(), request.prNumber()));
    }

    public record ExternalReviewRequest(String jiraKey, int prNumber) {}

    public record ExternalReviewResponse(
            String jiraKey,
            int prNumber,
            String jiraTitle,
            String jiraDescription,
            String modelId,
            String summary,
            boolean approved,
            int issueCount,
            int criticalCount,
            List<AiReviewIssue> issues,
            Instant reviewedAt
    ) {}
}
