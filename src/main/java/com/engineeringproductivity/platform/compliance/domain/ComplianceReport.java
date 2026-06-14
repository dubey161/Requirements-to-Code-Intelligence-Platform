package com.engineeringproductivity.platform.compliance.domain;

import com.engineeringproductivity.platform.common.persistence.ComplianceGapsConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "compliance_report")
public class ComplianceReport {

    @Id
    private UUID id;

    @Column(name = "requirement_id", nullable = false, unique = true)
    private UUID requirementId;

    @Column(name = "pr_number", nullable = false)
    private int prNumber;

    @Convert(converter = ComplianceGapsConverter.class)
    @Column(name = "gaps", nullable = false, columnDefinition = "text")
    private List<ComplianceGap> gaps;

    @Column(name = "compliance_score", nullable = false)
    private int complianceScore; // 0–100

    @Column(name = "checked_at", nullable = false)
    private Instant checkedAt;

    protected ComplianceReport() {}

    private ComplianceReport(UUID id, UUID requirementId, int prNumber,
                              List<ComplianceGap> gaps, int complianceScore, Instant checkedAt) {
        this.id = id;
        this.requirementId = requirementId;
        this.prNumber = prNumber;
        this.gaps = gaps;
        this.complianceScore = complianceScore;
        this.checkedAt = checkedAt;
    }

    public static ComplianceReport create(UUID requirementId, int prNumber,
                                           List<ComplianceGap> gaps, int complianceScore) {
        return new ComplianceReport(UUID.randomUUID(), requirementId, prNumber,
                gaps, complianceScore, Instant.now());
    }

    public void refresh(int prNumber, List<ComplianceGap> gaps, int complianceScore) {
        this.prNumber = prNumber;
        this.gaps = gaps;
        this.complianceScore = complianceScore;
        this.checkedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getRequirementId() { return requirementId; }
    public int getPrNumber() { return prNumber; }
    public List<ComplianceGap> getGaps() { return gaps; }
    public int getComplianceScore() { return complianceScore; }
    public Instant getCheckedAt() { return checkedAt; }
}
