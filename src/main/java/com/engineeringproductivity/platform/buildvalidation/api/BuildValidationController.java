package com.engineeringproductivity.platform.buildvalidation.api;

import com.engineeringproductivity.platform.buildvalidation.application.BuildValidationService;
import com.engineeringproductivity.platform.buildvalidation.domain.BuildValidationReport;
import com.engineeringproductivity.platform.buildvalidation.domain.ValidationCheck;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/requirements/{id}/build-validation")
public class BuildValidationController {

    private final BuildValidationService service;

    public BuildValidationController(BuildValidationService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    BuildValidationResponse trigger(@PathVariable UUID id) {
        return BuildValidationResponse.from(service.validate(id));
    }

    @GetMapping
    BuildValidationResponse get(@PathVariable UUID id) {
        return BuildValidationResponse.from(service.getReport(id));
    }

    public record BuildValidationResponse(
            UUID id, UUID requirementId,
            String status, int fileCount, int errorCount, int warningCount,
            List<ValidationCheck> checks, Instant validatedAt
    ) {
        static BuildValidationResponse from(BuildValidationReport r) {
            return new BuildValidationResponse(
                    r.getId(), r.getRequirementId(),
                    r.getStatus(), r.getFileCount(), r.getErrorCount(), r.getWarningCount(),
                    r.getChecks(), r.getValidatedAt()
            );
        }
    }
}
