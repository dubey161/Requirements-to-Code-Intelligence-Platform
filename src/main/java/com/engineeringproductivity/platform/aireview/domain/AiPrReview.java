package com.engineeringproductivity.platform.aireview.domain;

import com.engineeringproductivity.platform.common.persistence.AiReviewIssuesConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Stores every AI review output with full auditability:
 * model, prompt version, raw response, and structured issues.
 *
 * This fulfils the "Store AI Outputs" requirement — history, auditability,
 * reproducibility across model upgrades.
 */
@Entity
@Table(name = "ai_pr_review")
public class AiPrReview {

    @Id
    private UUID id;

    @Column(name = "requirement_id", nullable = false)
    private UUID requirementId;

    @Column(name = "pr_number", nullable = false)
    private int prNumber;

    @Column(name = "model_id", nullable = false, length = 100)
    private String modelId;

    @Column(name = "prompt_version", nullable = false, length = 20)
    private String promptVersion;

    @Column(name = "summary", nullable = false, columnDefinition = "text")
    private String summary;

    @Column(name = "approved", nullable = false)
    private boolean approved;

    @Convert(converter = AiReviewIssuesConverter.class)
    @Column(name = "issues", nullable = false, columnDefinition = "text")
    private List<AiReviewIssue> issues;

    @Column(name = "raw_response", columnDefinition = "text")
    private String rawResponse;

    @Column(name = "reviewed_at", nullable = false)
    private Instant reviewedAt;

    protected AiPrReview() {}

    private AiPrReview(UUID id, UUID requirementId, int prNumber, String modelId,
                        String promptVersion, String summary, boolean approved,
                        List<AiReviewIssue> issues, String rawResponse, Instant reviewedAt) {
        this.id = id;
        this.requirementId = requirementId;
        this.prNumber = prNumber;
        this.modelId = modelId;
        this.promptVersion = promptVersion;
        this.summary = summary;
        this.approved = approved;
        this.issues = issues;
        this.rawResponse = rawResponse;
        this.reviewedAt = reviewedAt;
    }

    public static AiPrReview create(UUID requirementId, int prNumber, String modelId,
                                     String promptVersion, String summary, boolean approved,
                                     List<AiReviewIssue> issues, String rawResponse) {
        return new AiPrReview(UUID.randomUUID(), requirementId, prNumber, modelId,
                promptVersion, summary, approved, issues, rawResponse, Instant.now());
    }

    public UUID getId() { return id; }
    public UUID getRequirementId() { return requirementId; }
    public int getPrNumber() { return prNumber; }
    public String getModelId() { return modelId; }
    public String getPromptVersion() { return promptVersion; }
    public String getSummary() { return summary; }
    public boolean isApproved() { return approved; }
    public List<AiReviewIssue> getIssues() { return issues; }
    public String getRawResponse() { return rawResponse; }
    public Instant getReviewedAt() { return reviewedAt; }
}
