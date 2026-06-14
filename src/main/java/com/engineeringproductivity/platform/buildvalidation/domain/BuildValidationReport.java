package com.engineeringproductivity.platform.buildvalidation.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "build_validation_report")
public class BuildValidationReport {

    @Id
    private UUID id;

    @Column(name = "requirement_id", nullable = false)
    private UUID requirementId;

    @Column(name = "status", nullable = false, length = 20)
    private String status; // PASSED | WARNING | FAILED

    @Column(name = "file_count", nullable = false)
    private int fileCount;

    @Column(name = "error_count", nullable = false)
    private int errorCount;

    @Column(name = "warning_count", nullable = false)
    private int warningCount;

    @Convert(converter = ValidationChecksConverter.class)
    @Column(name = "checks", nullable = false, columnDefinition = "text")
    private List<ValidationCheck> checks;

    @Column(name = "validated_at", nullable = false)
    private Instant validatedAt;

    protected BuildValidationReport() {}

    private BuildValidationReport(UUID id, UUID requirementId, String status,
                                   int fileCount, int errorCount, int warningCount,
                                   List<ValidationCheck> checks, Instant validatedAt) {
        this.id = id;
        this.requirementId = requirementId;
        this.status = status;
        this.fileCount = fileCount;
        this.errorCount = errorCount;
        this.warningCount = warningCount;
        this.checks = checks;
        this.validatedAt = validatedAt;
    }

    public static BuildValidationReport create(UUID requirementId, String status,
                                                int fileCount, int errorCount, int warningCount,
                                                List<ValidationCheck> checks) {
        return new BuildValidationReport(UUID.randomUUID(), requirementId, status,
                fileCount, errorCount, warningCount, checks, Instant.now());
    }

    public UUID getId()            { return id; }
    public UUID getRequirementId() { return requirementId; }
    public String getStatus()      { return status; }
    public int getFileCount()      { return fileCount; }
    public int getErrorCount()     { return errorCount; }
    public int getWarningCount()   { return warningCount; }
    public List<ValidationCheck> getChecks() { return checks; }
    public Instant getValidatedAt()  { return validatedAt; }
}
