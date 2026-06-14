package com.engineeringproductivity.platform.risk.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "risk_score")
public class RiskScore {

    @Id
    private UUID id;

    @Column(name = "requirement_id", nullable = false, unique = true)
    private UUID requirementId;

    @Column(name = "pr_number", nullable = false)
    private int prNumber;

    @Column(name = "overall_score", nullable = false)
    private int overallScore; // 0–100 (100 = highest risk)

    @Column(name = "compliance_contribution", nullable = false)
    private int complianceContribution;

    @Column(name = "security_contribution", nullable = false)
    private int securityContribution;

    @Column(name = "completeness_contribution", nullable = false)
    private int completenessContribution;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 20)
    private RiskLevel riskLevel;

    @Column(name = "recommendation", nullable = false, columnDefinition = "text")
    private String recommendation;

    @Column(name = "scored_at", nullable = false)
    private Instant scoredAt;

    public enum RiskLevel { LOW, MEDIUM, HIGH, CRITICAL }

    protected RiskScore() {}

    private RiskScore(UUID id, UUID requirementId, int prNumber, int overallScore,
                      int complianceContribution, int securityContribution, int completenessContribution,
                      RiskLevel riskLevel, String recommendation, Instant scoredAt) {
        this.id = id;
        this.requirementId = requirementId;
        this.prNumber = prNumber;
        this.overallScore = overallScore;
        this.complianceContribution = complianceContribution;
        this.securityContribution = securityContribution;
        this.completenessContribution = completenessContribution;
        this.riskLevel = riskLevel;
        this.recommendation = recommendation;
        this.scoredAt = scoredAt;
    }

    public static RiskScore create(UUID requirementId, int prNumber, int overallScore,
                                    int complianceContribution, int securityContribution,
                                    int completenessContribution, RiskLevel riskLevel, String recommendation) {
        return new RiskScore(UUID.randomUUID(), requirementId, prNumber, overallScore,
                complianceContribution, securityContribution, completenessContribution,
                riskLevel, recommendation, Instant.now());
    }

    public void refresh(int prNumber, int overallScore, int complianceContribution,
                        int securityContribution, int completenessContribution,
                        RiskLevel riskLevel, String recommendation) {
        this.prNumber = prNumber;
        this.overallScore = overallScore;
        this.complianceContribution = complianceContribution;
        this.securityContribution = securityContribution;
        this.completenessContribution = completenessContribution;
        this.riskLevel = riskLevel;
        this.recommendation = recommendation;
        this.scoredAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getRequirementId() { return requirementId; }
    public int getPrNumber() { return prNumber; }
    public int getOverallScore() { return overallScore; }
    public int getComplianceContribution() { return complianceContribution; }
    public int getSecurityContribution() { return securityContribution; }
    public int getCompletenessContribution() { return completenessContribution; }
    public RiskLevel getRiskLevel() { return riskLevel; }
    public String getRecommendation() { return recommendation; }
    public Instant getScoredAt() { return scoredAt; }
}
