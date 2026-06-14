package com.engineeringproductivity.platform.pipeline;

import com.engineeringproductivity.platform.buildvalidation.domain.BuildValidationRepository;
import com.engineeringproductivity.platform.codegen.domain.GeneratedCodeBundleRepository;
import com.engineeringproductivity.platform.compliance.domain.ComplianceReportRepository;
import com.engineeringproductivity.platform.github.domain.PullRequestRepository;
import com.engineeringproductivity.platform.requirement.analysis.domain.RequirementAnalysisRepository;
import com.engineeringproductivity.platform.requirement.domain.RequirementStory;
import com.engineeringproductivity.platform.requirement.domain.RequirementStoryRepository;
import com.engineeringproductivity.platform.risk.domain.RiskScoreRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Aggregates the full pipeline state for a requirement into a single response.
 * Primary data source for the React dashboard.
 */
@RestController
@RequestMapping("/api/v1/requirements")
public class PipelineStatusController {

    private final RequirementStoryRepository storyRepository;
    private final RequirementAnalysisRepository analysisRepository;
    private final GeneratedCodeBundleRepository codeRepository;
    private final BuildValidationRepository buildValidationRepository;
    private final PullRequestRepository prRepository;
    private final ComplianceReportRepository complianceRepository;
    private final RiskScoreRepository riskRepository;

    public PipelineStatusController(RequirementStoryRepository storyRepository,
                                     RequirementAnalysisRepository analysisRepository,
                                     GeneratedCodeBundleRepository codeRepository,
                                     BuildValidationRepository buildValidationRepository,
                                     PullRequestRepository prRepository,
                                     ComplianceReportRepository complianceRepository,
                                     RiskScoreRepository riskRepository) {
        this.storyRepository = storyRepository;
        this.analysisRepository = analysisRepository;
        this.codeRepository = codeRepository;
        this.buildValidationRepository = buildValidationRepository;
        this.prRepository = prRepository;
        this.complianceRepository = complianceRepository;
        this.riskRepository = riskRepository;
    }

    @GetMapping("/{id}/pipeline")
    PipelineStatus getPipeline(@PathVariable UUID id) {
        RequirementStory story = storyRepository.findById(id)
                .orElseThrow(() -> new com.engineeringproductivity.platform.common.api.ResourceNotFoundException(
                        "Requirement not found: " + id));

        var analysis   = analysisRepository.findByRequirementId(id);
        var code       = codeRepository.findByRequirementId(id);
        var buildVal   = buildValidationRepository.findByRequirementId(id);
        var pr         = prRepository.findByRequirementId(id);
        var compliance = complianceRepository.findByRequirementId(id);
        var risk       = riskRepository.findByRequirementId(id);

        // Extract requirementType from analysis model if available
        String requirementType = analysis
                .map(a -> a.getKnowledgeModel().resolvedType().name())
                .orElse("SPRING_CRUD");

        return new PipelineStatus(
                story.getId(),
                story.getExternalKey(),
                story.getTitle(),
                story.getStatus().name(),
                requirementType,
                new StageStatus("INTAKE", "DONE", null),
                new StageStatus("ANALYSIS",
                        analysis.isPresent() ? "DONE" : "PENDING",
                        analysis.map(a -> a.getAnalyzedAt().toString()).orElse(null)),
                new StageStatus("CODE_GENERATION",
                        code.isPresent() ? "DONE" : "PENDING",
                        code.map(c -> c.getGeneratedFiles().size() + " files").orElse(null)),
                new StageStatus("BUILD_VALIDATION",
                        buildVal.isPresent() ? "DONE" : "PENDING",
                        buildVal.map(b -> b.getStatus() + " · " + b.getFileCount() + " files").orElse(null)),
                new StageStatus("GITHUB_PR",
                        pr.isPresent() ? "DONE" : "PENDING",
                        pr.map(p -> "PR #" + p.getPrNumber()).orElse(null)),
                new StageStatus("COMPLIANCE",
                        compliance.isPresent() ? "DONE" : "PENDING",
                        compliance.map(c -> "Score: " + c.getComplianceScore() + "/100").orElse(null)),
                new StageStatus("RISK_SCORING",
                        risk.isPresent() ? "DONE" : "PENDING",
                        risk.map(r -> r.getRiskLevel().name() + " (" + r.getOverallScore() + "/100)").orElse(null)),
                risk.map(r -> r.getOverallScore()).orElse(null),
                risk.map(r -> r.getRiskLevel().name()).orElse(null),
                compliance.map(c -> c.getComplianceScore()).orElse(null),
                pr.map(p -> p.getHtmlUrl()).orElse(null),
                pr.map(p -> p.getPrNumber()).orElse(null)
        );
    }

    @GetMapping("/pipeline")
    List<PipelineStatus> getAllPipelines() {
        return storyRepository.findAll().stream()
                .map(s -> getPipeline(s.getId()))
                .toList();
    }

    public record PipelineStatus(
            UUID requirementId,
            String externalKey,
            String title,
            String requirementStatus,
            String requirementType,
            StageStatus intake,
            StageStatus analysis,
            StageStatus codeGeneration,
            StageStatus buildValidation,
            StageStatus githubPr,
            StageStatus compliance,
            StageStatus riskScoring,
            Integer riskScore,
            String riskLevel,
            Integer complianceScore,
            String prUrl,
            Integer prNumber
    ) {}

    public record StageStatus(String stage, String status, String detail) {}
}
