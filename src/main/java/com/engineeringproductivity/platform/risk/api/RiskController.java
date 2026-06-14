package com.engineeringproductivity.platform.risk.api;

import com.engineeringproductivity.platform.risk.application.RiskScoringService;
import com.engineeringproductivity.platform.risk.domain.RiskScore;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/requirements/{requirementId}/risk")
public class RiskController {

    private final RiskScoringService service;

    public RiskController(RiskScoringService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    RiskScoreResponse score(@PathVariable UUID requirementId) {
        return RiskScoreResponse.from(service.score(requirementId));
    }

    @GetMapping
    RiskScoreResponse getRisk(@PathVariable UUID requirementId) {
        return RiskScoreResponse.from(service.getRiskScore(requirementId));
    }

    public record RiskScoreResponse(
            UUID id, UUID requirementId, int prNumber,
            int overallScore, int complianceContribution,
            int securityContribution, int completenessContribution,
            String riskLevel, String recommendation, Instant scoredAt
    ) {
        static RiskScoreResponse from(RiskScore r) {
            return new RiskScoreResponse(r.getId(), r.getRequirementId(), r.getPrNumber(),
                    r.getOverallScore(), r.getComplianceContribution(), r.getSecurityContribution(),
                    r.getCompletenessContribution(), r.getRiskLevel().name(),
                    r.getRecommendation(), r.getScoredAt());
        }
    }
}
