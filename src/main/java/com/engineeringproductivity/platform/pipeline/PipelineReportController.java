package com.engineeringproductivity.platform.pipeline;

import com.engineeringproductivity.platform.aireview.domain.AiPrReviewRepository;
import com.engineeringproductivity.platform.codegen.domain.GeneratedCodeBundle;
import com.engineeringproductivity.platform.codegen.domain.GeneratedCodeBundleRepository;
import com.engineeringproductivity.platform.compliance.domain.ComplianceReport;
import com.engineeringproductivity.platform.compliance.domain.ComplianceReportRepository;
import com.engineeringproductivity.platform.github.domain.CreatedPullRequest;
import com.engineeringproductivity.platform.github.domain.PullRequestRepository;
import com.engineeringproductivity.platform.common.api.ResourceNotFoundException;
import com.engineeringproductivity.platform.requirement.analysis.domain.RequirementAnalysis;
import com.engineeringproductivity.platform.requirement.analysis.domain.RequirementAnalysisRepository;
import com.engineeringproductivity.platform.requirement.domain.RequirementStory;
import com.engineeringproductivity.platform.requirement.domain.RequirementStoryRepository;
import com.engineeringproductivity.platform.risk.domain.RiskScore;
import com.engineeringproductivity.platform.risk.domain.RiskScoreRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Aggregates a full pipeline report for PDF/export generation.
 */
@RestController
@RequestMapping("/api/v1/requirements")
public class PipelineReportController {

    private final RequirementStoryRepository storyRepository;
    private final RequirementAnalysisRepository analysisRepository;
    private final GeneratedCodeBundleRepository codeRepository;
    private final PullRequestRepository prRepository;
    private final ComplianceReportRepository complianceRepository;
    private final RiskScoreRepository riskRepository;
    private final AiPrReviewRepository aiReviewRepository;

    public PipelineReportController(RequirementStoryRepository storyRepository,
                                     RequirementAnalysisRepository analysisRepository,
                                     GeneratedCodeBundleRepository codeRepository,
                                     PullRequestRepository prRepository,
                                     ComplianceReportRepository complianceRepository,
                                     RiskScoreRepository riskRepository,
                                     AiPrReviewRepository aiReviewRepository) {
        this.storyRepository = storyRepository;
        this.analysisRepository = analysisRepository;
        this.codeRepository = codeRepository;
        this.prRepository = prRepository;
        this.complianceRepository = complianceRepository;
        this.riskRepository = riskRepository;
        this.aiReviewRepository = aiReviewRepository;
    }

    @GetMapping("/{id}/report")
    public Map<String, Object> getReport(@PathVariable UUID id) {
        RequirementStory story = storyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Requirement not found: " + id));

        var analysis   = analysisRepository.findByRequirementId(id);
        var code       = codeRepository.findByRequirementId(id);
        var pr         = prRepository.findByRequirementId(id);
        var compliance = complianceRepository.findByRequirementId(id);
        var risk       = riskRepository.findByRequirementId(id);
        var aiReview   = aiReviewRepository.findTopByRequirementIdOrderByReviewedAtDesc(id);

        return Map.of(
            "generatedAt", Instant.now(),
            "requirement", Map.of(
                "id",                 story.getId(),
                "externalKey",        story.getExternalKey(),
                "title",              story.getTitle(),
                "description",        story.getDescription(),
                "acceptanceCriteria", story.getAcceptanceCriteria(),
                "status",             story.getStatus(),
                "createdAt",          story.getCreatedAt()
            ),
            "analysis", analysis.map(a -> Map.of(
                "analyzerVersion",        a.getAnalyzerVersion(),
                "analyzedAt",             a.getAnalyzedAt(),
                "entities",               a.getKnowledgeModel().entities(),
                "apiEndpoints",           a.getKnowledgeModel().apiEndpoints(),
                "validationRules",        a.getKnowledgeModel().validationRules(),
                "securityRequirements",   a.getKnowledgeModel().securityRequirements(),
                "functionalRequirements", a.getKnowledgeModel().functionalRequirements(),
                "edgeCases",              a.getKnowledgeModel().edgeCases()
            )).orElse(Map.of()),
            "codeGeneration", code.map(c -> Map.of(
                "targetPackage", c.getTargetPackage(),
                "fileCount",     c.getGeneratedFiles().size(),
                "generatedAt",   c.getGeneratedAt(),
                "files",         c.getGeneratedFiles().stream()
                    .map(f -> Map.of("fileName", f.fileName(), "fileType", f.fileType().name()))
                    .toList()
            )).orElse(Map.of()),
            "pullRequest", pr.map(p -> Map.of(
                "prNumber",  p.getPrNumber(),
                "htmlUrl",   p.getHtmlUrl(),
                "headBranch",p.getHeadBranch(),
                "createdAt", p.getCreatedAt()
            )).orElse(Map.of()),
            "compliance", compliance.map(c -> Map.of(
                "complianceScore", c.getComplianceScore(),
                "gapCount",        c.getGaps().size(),
                "criticalGaps",    c.getGaps().stream().filter(g -> "CRITICAL".equals(g.severity())).count(),
                "checkedAt",       c.getCheckedAt(),
                "gaps",            c.getGaps()
            )).orElse(Map.of()),
            "risk", risk.map(r -> Map.of(
                "riskLevel",                r.getRiskLevel(),
                "overallScore",             r.getOverallScore(),
                "complianceContribution",   r.getComplianceContribution(),
                "securityContribution",     r.getSecurityContribution(),
                "completenessContribution", r.getCompletenessContribution(),
                "recommendation",           r.getRecommendation(),
                "scoredAt",                 r.getScoredAt()
            )).orElse(Map.of()),
            "aiReview", aiReview.map(a -> Map.of(
                "modelId",       a.getModelId(),
                "summary",       a.getSummary(),
                "approved",      a.isApproved(),
                "issueCount",    a.getIssues().size(),
                "criticalCount", a.getIssues().stream().filter(i -> "CRITICAL".equals(i.severity())).count(),
                "issues",        a.getIssues(),
                "reviewedAt",    a.getReviewedAt()
            )).orElse(Map.of())
        );
    }
}
