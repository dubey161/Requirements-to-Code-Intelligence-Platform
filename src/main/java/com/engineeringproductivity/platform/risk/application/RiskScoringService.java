package com.engineeringproductivity.platform.risk.application;

import com.engineeringproductivity.platform.common.api.ResourceNotFoundException;
import com.engineeringproductivity.platform.compliance.application.ComplianceService;
import com.engineeringproductivity.platform.compliance.domain.ComplianceGap;
import com.engineeringproductivity.platform.compliance.domain.ComplianceReport;
import com.engineeringproductivity.platform.github.GitHubClient;
import com.engineeringproductivity.platform.github.domain.PullRequestRepository;
import com.engineeringproductivity.platform.requirement.analysis.application.RequirementAnalyzerService;
import com.engineeringproductivity.platform.requirement.analysis.domain.model.RequirementKnowledgeModel;
import com.engineeringproductivity.platform.risk.domain.RiskScore;
import com.engineeringproductivity.platform.risk.domain.RiskScore.RiskLevel;
import com.engineeringproductivity.platform.risk.domain.RiskScoreRepository;
import com.engineeringproductivity.platform.search.ElasticsearchIndexingService;
import org.slf4j.Logger;
import java.util.Optional;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class RiskScoringService {

    private static final Logger log = LoggerFactory.getLogger(RiskScoringService.class);

    private final ComplianceService complianceService;
    private final RequirementAnalyzerService analyzerService;
    private final PullRequestRepository prRepository;
    private final GitHubClient gitHubClient;
    private final RiskScoreRepository riskScoreRepository;
    private final Optional<ElasticsearchIndexingService> indexingService;

    public RiskScoringService(ComplianceService complianceService,
                               RequirementAnalyzerService analyzerService,
                               PullRequestRepository prRepository,
                               GitHubClient gitHubClient,
                               RiskScoreRepository riskScoreRepository,
                               Optional<ElasticsearchIndexingService> indexingService) {
        this.complianceService = complianceService;
        this.analyzerService = analyzerService;
        this.prRepository = prRepository;
        this.gitHubClient = gitHubClient;
        this.riskScoreRepository = riskScoreRepository;
        this.indexingService = indexingService;
    }

    @Transactional
    public RiskScore score(UUID requirementId) {
        ComplianceReport complianceReport = complianceService.getReport(requirementId);
        RequirementKnowledgeModel model = analyzerService.getAnalysis(requirementId).getKnowledgeModel();

        var pr = prRepository.findByRequirementId(requirementId)
                .orElseThrow(() -> new ResourceNotFoundException("No PR found for: " + requirementId));

        List<GitHubClient.PrFile> prFiles = gitHubClient.getPrFiles(pr.getPrNumber());
        String allPatch = prFiles.stream().map(GitHubClient.PrFile::patch).reduce("", String::concat);

        // ── Compliance component (40%) ────────────────────────────────────────
        int complianceScore = complianceReport.getComplianceScore(); // 0-100 (100=compliant)
        int complianceRisk = 100 - complianceScore; // invert: 100=max risk

        // ── Security component (35%) ──────────────────────────────────────────
        long criticalSecurityGaps = complianceReport.getGaps().stream()
                .filter(g -> g.category() == ComplianceGap.GapCategory.MISSING_SECURITY
                        && g.severity() == ComplianceGap.Severity.CRITICAL)
                .count();
        int securityRisk = (int) Math.min(100, criticalSecurityGaps * 25);

        // ── Completeness component (25%) ──────────────────────────────────────
        int expectedEntities = model.entities().size();
        int expectedEndpoints = model.apiEndpoints().size();
        long missingEntities = complianceReport.getGaps().stream()
                .filter(g -> g.category() == ComplianceGap.GapCategory.MISSING_ENTITY).count();
        int completenessRisk = expectedEntities > 0
                ? (int) (((double) missingEntities / expectedEntities) * 100) : 0;

        // ── Weighted overall ──────────────────────────────────────────────────
        int overallScore = (int) Math.min(100,
                (complianceRisk * 0.40) + (securityRisk * 0.35) + (completenessRisk * 0.25));

        RiskLevel level = overallScore >= 70 ? RiskLevel.CRITICAL
                : overallScore >= 50 ? RiskLevel.HIGH
                : overallScore >= 25 ? RiskLevel.MEDIUM
                : RiskLevel.LOW;

        String recommendation = buildRecommendation(level, complianceReport, model);

        log.info("Risk score for requirement={}: overall={}, level={}", requirementId, overallScore, level);

        // Post as PR comment
        gitHubClient.createReviewComment(pr.getPrNumber(), formatPrComment(overallScore, level,
                complianceScore, complianceReport.getGaps()));

        RiskScore risk = riskScoreRepository.findByRequirementId(requirementId)
                .map(existing -> {
                    existing.refresh(pr.getPrNumber(), overallScore, complianceRisk,
                            securityRisk, completenessRisk, level, recommendation);
                    return riskScoreRepository.save(existing);
                })
                .orElseGet(() -> riskScoreRepository.save(
                        RiskScore.create(requirementId, pr.getPrNumber(), overallScore,
                                complianceRisk, securityRisk, completenessRisk, level, recommendation)));

        indexingService.ifPresent(s -> s.updateRiskScore(requirementId, overallScore, level.name()));

        return risk;
    }

    @Transactional(readOnly = true)
    public RiskScore getRiskScore(UUID requirementId) {
        return riskScoreRepository.findByRequirementId(requirementId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No risk score for requirement: " + requirementId +
                        ". Trigger via POST /api/v1/requirements/{id}/risk"));
    }

    private String buildRecommendation(RiskLevel level, ComplianceReport report,
                                        RequirementKnowledgeModel model) {
        return switch (level) {
            case CRITICAL -> "DO NOT MERGE. Critical compliance gaps detected. Address all CRITICAL and HIGH severity gaps before merging.";
            case HIGH -> "Block merge until HIGH severity gaps are resolved. Review security requirements carefully.";
            case MEDIUM -> "Merge with caution. Review MEDIUM gaps and ensure test coverage is adequate.";
            case LOW -> "Safe to merge. Minor improvements possible but not blocking.";
        };
    }

    private String formatPrComment(int overallScore, RiskLevel level,
                                    int complianceScore, List<ComplianceGap> gaps) {
        String emoji = switch (level) {
            case CRITICAL -> "🚨";
            case HIGH -> "❌";
            case MEDIUM -> "⚠️";
            case LOW -> "✅";
        };

        StringBuilder sb = new StringBuilder();
        sb.append("## %s AI Engineering Productivity Platform — Risk Assessment\n\n".formatted(emoji));
        sb.append("| Metric | Score |\n|---|---|\n");
        sb.append("| **Risk Score** | %d/100 (%s) |\n".formatted(overallScore, level));
        sb.append("| **Compliance Score** | %d/100 |\n".formatted(complianceScore));
        sb.append("| **Gaps Found** | %d |\n\n".formatted(gaps.size()));

        long critical = gaps.stream().filter(g -> g.severity() == ComplianceGap.Severity.CRITICAL).count();
        long high = gaps.stream().filter(g -> g.severity() == ComplianceGap.Severity.HIGH).count();

        if (critical > 0 || high > 0) {
            sb.append("### Critical/High Gaps\n");
            gaps.stream()
                    .filter(g -> g.severity() == ComplianceGap.Severity.CRITICAL ||
                            g.severity() == ComplianceGap.Severity.HIGH)
                    .forEach(g -> sb.append("- **[%s]** %s\n".formatted(g.severity(), g.description())));
            sb.append("\n");
        }

        sb.append("*Generated by AI Engineering Productivity Platform*");
        return sb.toString();
    }
}
