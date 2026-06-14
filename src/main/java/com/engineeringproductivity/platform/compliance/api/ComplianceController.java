package com.engineeringproductivity.platform.compliance.api;

import com.engineeringproductivity.platform.compliance.application.ComplianceService;
import com.engineeringproductivity.platform.compliance.domain.ComplianceGap;
import com.engineeringproductivity.platform.compliance.domain.ComplianceReport;
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
@RequestMapping("/api/v1/requirements/{requirementId}/compliance")
public class ComplianceController {

    private final ComplianceService service;

    public ComplianceController(ComplianceService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    ComplianceReportResponse check(@PathVariable UUID requirementId) {
        return ComplianceReportResponse.from(service.check(requirementId));
    }

    @GetMapping
    ComplianceReportResponse getReport(@PathVariable UUID requirementId) {
        return ComplianceReportResponse.from(service.getReport(requirementId));
    }

    public record ComplianceReportResponse(
            UUID reportId, UUID requirementId, int prNumber,
            int complianceScore, int gapCount, int criticalGaps,
            List<ComplianceGap> gaps, Instant checkedAt
    ) {
        static ComplianceReportResponse from(ComplianceReport r) {
            long critical = r.getGaps().stream()
                    .filter(g -> g.severity() == ComplianceGap.Severity.CRITICAL).count();
            return new ComplianceReportResponse(r.getId(), r.getRequirementId(), r.getPrNumber(),
                    r.getComplianceScore(), r.getGaps().size(), (int) critical,
                    r.getGaps(), r.getCheckedAt());
        }
    }
}
