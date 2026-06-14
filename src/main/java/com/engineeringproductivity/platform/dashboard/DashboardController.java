package com.engineeringproductivity.platform.dashboard;

import com.engineeringproductivity.platform.aireview.domain.AiPrReviewRepository;
import com.engineeringproductivity.platform.auth.domain.User;
import com.engineeringproductivity.platform.auth.domain.UserRepository;
import com.engineeringproductivity.platform.compliance.domain.ComplianceReport;
import com.engineeringproductivity.platform.compliance.domain.ComplianceReportRepository;
import com.engineeringproductivity.platform.github.domain.PullRequestRepository;
import com.engineeringproductivity.platform.requirement.domain.RequirementStatus;
import com.engineeringproductivity.platform.requirement.domain.RequirementStory;
import com.engineeringproductivity.platform.requirement.domain.RequirementStoryRepository;
import com.engineeringproductivity.platform.risk.domain.RiskScore;
import com.engineeringproductivity.platform.risk.domain.RiskScoreRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final RequirementStoryRepository requirementRepo;
    private final PullRequestRepository prRepo;
    private final ComplianceReportRepository complianceRepo;
    private final RiskScoreRepository riskRepo;
    private final AiPrReviewRepository aiReviewRepo;
    private final UserRepository userRepo;

    public DashboardController(RequirementStoryRepository requirementRepo,
                                PullRequestRepository prRepo,
                                ComplianceReportRepository complianceRepo,
                                RiskScoreRepository riskRepo,
                                AiPrReviewRepository aiReviewRepo,
                                UserRepository userRepo) {
        this.requirementRepo = requirementRepo;
        this.prRepo = prRepo;
        this.complianceRepo = complianceRepo;
        this.riskRepo = riskRepo;
        this.aiReviewRepo = aiReviewRepo;
        this.userRepo = userRepo;
    }

    // ── Developer Dashboard ───────────────────────────────────────────────────

    /**
     * GET /api/v1/dashboard/developer
     * Shows stats scoped to the currently authenticated developer.
     * Accessible to: DEVELOPER, ENGINEERING_MANAGER, ADMIN
     */
    @GetMapping("/developer")
    public Map<String, Object> developerDashboard(@AuthenticationPrincipal User currentUser) {
        List<RequirementStory> myRequirements = requirementRepo
                .findTop20ByCreatedByOrderByCreatedAtDesc(currentUser.getId());

        long totalCreated = requirementRepo.countByCreatedBy(currentUser.getId());
        long generatedPrs = prRepo.countByRequirementCreatedBy(currentUser.getId());

        // Average compliance for my requirements
        List<ComplianceReport> myReports = complianceRepo.findByRequirementCreatedBy(currentUser.getId());
        OptionalDouble avgCompliance = myReports.stream()
                .mapToInt(ComplianceReport::getComplianceScore)
                .average();

        // Most common risk level for my requirements
        List<RiskScore> myRisks = riskRepo.findByRequirementCreatedBy(currentUser.getId());
        String dominantRiskLevel = myRisks.stream()
                .collect(Collectors.groupingBy(r -> r.getRiskLevel().name(), Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("N/A");

        return Map.of(
                "userId", currentUser.getId(),
                "email", currentUser.getEmail(),
                "requirementsCreated", totalCreated,
                "generatedPrs", generatedPrs,
                "averageComplianceScore", avgCompliance.isPresent()
                        ? Math.round(avgCompliance.getAsDouble()) : "N/A",
                "dominantRiskLevel", dominantRiskLevel,
                "recentRequirements", myRequirements.stream().map(this::toRequirementSummary).toList()
        );
    }

    // ── Engineering Manager Dashboard ─────────────────────────────────────────

    /**
     * GET /api/v1/dashboard/manager
     * Aggregate view across all requirements.
     * Accessible to: ENGINEERING_MANAGER, ADMIN
     */
    @GetMapping("/manager")
    public Map<String, Object> managerDashboard() {
        long totalRequirements  = requirementRepo.count();
        long analyzedCount      = requirementRepo.countByStatus(RequirementStatus.ANALYZED);
        long failedCount        = requirementRepo.countByStatus(RequirementStatus.ANALYSIS_FAILED);
        long totalPrs           = prRepo.count();
        long totalAiReviews     = aiReviewRepo.count();

        List<ComplianceReport> allReports = complianceRepo.findAll();
        OptionalDouble avgCompliance = allReports.stream()
                .mapToInt(ComplianceReport::getComplianceScore).average();

        // Risk distribution
        Map<String, Long> riskDistribution = riskRepo.findAll().stream()
                .collect(Collectors.groupingBy(r -> r.getRiskLevel().name(), Collectors.counting()));

        // Top contributors (by requirements created)
        Map<String, Long> topContributors = new LinkedHashMap<>();
        requirementRepo.findAll().stream()
                .filter(r -> r.getCreatedBy() != null)
                .collect(Collectors.groupingBy(r -> r.getCreatedBy().toString(), Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .forEach(e -> topContributors.put(e.getKey(), e.getValue()));

        // Compliance trend — last 10 reports ordered by score
        List<Map<String, Object>> complianceTrend = allReports.stream()
                .sorted((a, b) -> b.getCheckedAt().compareTo(a.getCheckedAt()))
                .limit(10)
                .map(r -> Map.<String, Object>of(
                        "requirementId", r.getRequirementId(),
                        "score", r.getComplianceScore(),
                        "checkedAt", r.getCheckedAt()))
                .toList();

        return Map.of(
                "totalRequirements", totalRequirements,
                "analyzedRequirements", analyzedCount,
                "analysisFailed", failedCount,
                "totalPullRequests", totalPrs,
                "totalAiReviews", totalAiReviews,
                "averageComplianceScore", avgCompliance.isPresent()
                        ? Math.round(avgCompliance.getAsDouble()) : "N/A",
                "riskDistribution", riskDistribution,
                "topContributors", topContributors,
                "complianceTrend", complianceTrend
        );
    }

    // ── Admin Dashboard ───────────────────────────────────────────────────────

    /**
     * GET /api/v1/dashboard/admin
     * System-wide view including user management stats.
     * Accessible to: ADMIN only
     */
    @GetMapping("/admin")
    public Map<String, Object> adminDashboard() {
        long totalUsers = userRepo.count();

        // Users by role
        Map<String, Long> usersByRole = userRepo.countGroupByRole().stream()
                .collect(Collectors.toMap(
                        row -> row[0].toString(),
                        row -> (Long) row[1]
                ));

        long activeUsers = userRepo.findAllByOrderByCreatedAtDesc().stream()
                .filter(User::isActive).count();

        long totalRequirements = requirementRepo.count();
        long totalPrs          = prRepo.count();
        long totalAiReviews    = aiReviewRepo.count();
        long totalCompliance   = complianceRepo.count();
        long totalRiskScores   = riskRepo.count();

        List<ComplianceReport> allReports = complianceRepo.findAll();
        OptionalDouble avgCompliance = allReports.stream()
                .mapToInt(ComplianceReport::getComplianceScore).average();

        Map<String, Long> riskDistribution = riskRepo.findAll().stream()
                .collect(Collectors.groupingBy(r -> r.getRiskLevel().name(), Collectors.counting()));

        String dominantRisk = riskDistribution.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalUsers",          totalUsers);
        result.put("activeUsers",         activeUsers);
        result.put("usersByRole",         usersByRole);
        result.put("totalRequirements",   totalRequirements);
        result.put("totalPullRequests",   totalPrs);
        result.put("totalAiReviews",      totalAiReviews);
        result.put("totalComplianceChecks", totalCompliance);
        result.put("totalRiskScores",     totalRiskScores);
        result.put("avgComplianceScore",  avgCompliance.isPresent()
                ? Math.round(avgCompliance.getAsDouble()) : null);
        result.put("dominantRiskLevel",   dominantRisk);
        result.put("riskDistribution",    riskDistribution);
        return result;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Map<String, Object> toRequirementSummary(RequirementStory r) {
        return Map.of(
                "id", r.getId(),
                "externalKey", r.getExternalKey(),
                "title", r.getTitle(),
                "status", r.getStatus(),
                "createdAt", r.getCreatedAt()
        );
    }
}
