package com.engineeringproductivity.platform.requirement.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "requirement_story")
public class RequirementStory {

    @Id
    private UUID id;

    @Column(name = "external_key", nullable = false, unique = true, length = 100)
    private String externalKey;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "requirement_acceptance_criterion", joinColumns = @JoinColumn(name = "requirement_id"))
    @OrderColumn(name = "criterion_order")
    @Column(name = "criterion", nullable = false, columnDefinition = "text")
    private List<String> acceptanceCriteria = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RequirementStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** The user who created this requirement. Nullable — set for all requirements created after auth was added. */
    @Column(name = "created_by")
    private UUID createdBy;

    @Version
    private long version;

    protected RequirementStory() {
    }

    private RequirementStory(
            UUID id,
            String externalKey,
            String title,
            String description,
            List<String> acceptanceCriteria,
            RequirementStatus status,
            Instant createdAt
    ) {
        this.id = id;
        this.externalKey = externalKey;
        this.title = title;
        this.description = description;
        this.acceptanceCriteria.addAll(acceptanceCriteria);
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public static RequirementStory receive(
            String externalKey,
            String title,
            String description,
            List<String> acceptanceCriteria
    ) {
        return receive(externalKey, title, description, acceptanceCriteria, null);
    }

    public static RequirementStory receive(
            String externalKey,
            String title,
            String description,
            List<String> acceptanceCriteria,
            UUID createdBy
    ) {
        RequirementStory story = new RequirementStory(
                UUID.randomUUID(),
                externalKey,
                title,
                description,
                List.copyOf(acceptanceCriteria),
                RequirementStatus.ANALYSIS_PENDING,
                Instant.now()
        );
        story.createdBy = createdBy;
        return story;
    }

    public UUID getId() {
        return id;
    }

    public String getExternalKey() {
        return externalKey;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getAcceptanceCriteria() {
        return List.copyOf(acceptanceCriteria);
    }

    public RequirementStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void markAnalyzed() {
        if (this.status != RequirementStatus.ANALYSIS_PENDING) {
            throw new IllegalStateException(
                    "Cannot mark as analyzed from status: " + this.status);
        }
        this.status = RequirementStatus.ANALYZED;
        this.updatedAt = Instant.now();
    }

    public void markAnalysisFailed() {
        this.status = RequirementStatus.ANALYSIS_FAILED;
        this.updatedAt = Instant.now();
    }

    public void resetForReanalysis() {
        if (this.status != RequirementStatus.ANALYZED && this.status != RequirementStatus.ANALYSIS_FAILED) {
            throw new IllegalStateException(
                    "Can only re-analyze from ANALYZED or ANALYSIS_FAILED state, current: " + this.status);
        }
        this.status = RequirementStatus.ANALYSIS_PENDING;
        this.updatedAt = Instant.now();
    }
}
