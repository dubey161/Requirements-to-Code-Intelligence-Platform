package com.engineeringproductivity.platform.aireview.api;

import com.engineeringproductivity.platform.aireview.application.AiPrReviewService;
import com.engineeringproductivity.platform.aireview.domain.AiPrReview;
import com.engineeringproductivity.platform.aireview.domain.AiReviewIssue;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/requirements/{requirementId}/ai-review")
public class AiPrReviewController {

    private final AiPrReviewService service;

    public AiPrReviewController(AiPrReviewService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    AiReviewResponse triggerReview(@PathVariable UUID requirementId) {
        return AiReviewResponse.from(service.review(requirementId));
    }

    @GetMapping
    AiReviewResponse getLatest(@PathVariable UUID requirementId) {
        return AiReviewResponse.from(service.getLatestReview(requirementId));
    }

    @GetMapping("/history")
    List<AiReviewResponse> getHistory(@PathVariable UUID requirementId) {
        return service.getReviewHistory(requirementId).stream()
                .map(AiReviewResponse::from).toList();
    }

    public record AiReviewResponse(
            UUID id, UUID requirementId, int prNumber,
            String modelId, String promptVersion,
            String summary, boolean approved,
            int issueCount, int criticalCount,
            List<AiReviewIssue> issues,
            Instant reviewedAt
    ) {
        static AiReviewResponse from(AiPrReview r) {
            long critical = r.getIssues().stream()
                    .filter(i -> "CRITICAL".equals(i.severity())).count();
            return new AiReviewResponse(
                    r.getId(), r.getRequirementId(), r.getPrNumber(),
                    r.getModelId(), r.getPromptVersion(),
                    r.getSummary(), r.isApproved(),
                    r.getIssues().size(), (int) critical,
                    r.getIssues(), r.getReviewedAt()
            );
        }
    }
}
