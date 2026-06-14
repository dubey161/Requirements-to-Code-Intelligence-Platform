package com.engineeringproductivity.platform.compliance.application;

import com.engineeringproductivity.platform.common.api.ResourceNotFoundException;
import com.engineeringproductivity.platform.compliance.domain.ComplianceReport;
import com.engineeringproductivity.platform.compliance.domain.ComplianceReportRepository;
import com.engineeringproductivity.platform.github.GitHubClient;
import com.engineeringproductivity.platform.github.domain.PullRequestRepository;
import com.engineeringproductivity.platform.requirement.analysis.application.RequirementAnalyzerService;
import com.engineeringproductivity.platform.requirement.analysis.domain.RequirementAnalysis;
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
public class ComplianceService {

    private static final Logger log = LoggerFactory.getLogger(ComplianceService.class);

    private final RequirementAnalyzerService analyzerService;
    private final PullRequestRepository prRepository;
    private final GitHubClient gitHubClient;
    private final ComplianceChecker checker;
    private final ComplianceReportRepository reportRepository;
    private final Optional<ElasticsearchIndexingService> indexingService;

    public ComplianceService(RequirementAnalyzerService analyzerService,
                              PullRequestRepository prRepository,
                              GitHubClient gitHubClient,
                              ComplianceChecker checker,
                              ComplianceReportRepository reportRepository,
                              Optional<ElasticsearchIndexingService> indexingService) {
        this.analyzerService = analyzerService;
        this.prRepository = prRepository;
        this.gitHubClient = gitHubClient;
        this.checker = checker;
        this.reportRepository = reportRepository;
        this.indexingService = indexingService;
    }

    @Transactional
    public ComplianceReport check(UUID requirementId) {
        RequirementAnalysis analysis = analyzerService.getAnalysis(requirementId);

        var pr = prRepository.findByRequirementId(requirementId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No PR found for requirement: " + requirementId));

        log.info("Checking compliance for requirement={} pr={}", requirementId, pr.getPrNumber());

        List<GitHubClient.PrFile> prFiles = gitHubClient.getPrFiles(pr.getPrNumber());
        ComplianceChecker.ComplianceResult result = checker.check(analysis.getKnowledgeModel(), prFiles);

        log.info("Compliance check: score={}, gaps={}", result.score(), result.gaps().size());

        ComplianceReport report = reportRepository.findByRequirementId(requirementId)
                .map(existing -> {
                    existing.refresh(pr.getPrNumber(), result.gaps(), result.score());
                    return reportRepository.save(existing);
                })
                .orElseGet(() -> reportRepository.save(
                        ComplianceReport.create(requirementId, pr.getPrNumber(),
                                result.gaps(), result.score())));

        indexingService.ifPresent(s -> s.updateComplianceScore(requirementId, result.score()));

        return report;
    }

    @Transactional(readOnly = true)
    public ComplianceReport getReport(UUID requirementId) {
        return reportRepository.findByRequirementId(requirementId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No compliance report for requirement: " + requirementId +
                        ". Trigger via POST /api/v1/requirements/{id}/compliance"));
    }
}
