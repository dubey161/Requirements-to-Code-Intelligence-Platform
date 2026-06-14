package com.engineeringproductivity.platform.requirement.analysis.domain;

import com.engineeringproductivity.platform.common.persistence.KnowledgeModelConverter;
import com.engineeringproductivity.platform.requirement.analysis.domain.model.RequirementKnowledgeModel;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "requirement_analysis")
public class RequirementAnalysis {

    @Id
    private UUID id;

    @Column(name = "requirement_id", nullable = false, unique = true)
    private UUID requirementId;

    @Convert(converter = KnowledgeModelConverter.class)
    @Column(name = "knowledge_model", nullable = false, columnDefinition = "text")
    private RequirementKnowledgeModel knowledgeModel;

    @Column(name = "analyzer_version", nullable = false, length = 100)
    private String analyzerVersion;

    @Column(name = "analyzed_at", nullable = false)
    private Instant analyzedAt;

    protected RequirementAnalysis() {
    }

    private RequirementAnalysis(
            UUID id,
            UUID requirementId,
            RequirementKnowledgeModel knowledgeModel,
            String analyzerVersion,
            Instant analyzedAt
    ) {
        this.id = id;
        this.requirementId = requirementId;
        this.knowledgeModel = knowledgeModel;
        this.analyzerVersion = analyzerVersion;
        this.analyzedAt = analyzedAt;
    }

    public static RequirementAnalysis create(
            UUID requirementId,
            RequirementKnowledgeModel knowledgeModel,
            String analyzerVersion
    ) {
        return new RequirementAnalysis(
                UUID.randomUUID(),
                requirementId,
                knowledgeModel,
                analyzerVersion,
                Instant.now()
        );
    }

    public void refresh(RequirementKnowledgeModel knowledgeModel, String analyzerVersion) {
        this.knowledgeModel = knowledgeModel;
        this.analyzerVersion = analyzerVersion;
        this.analyzedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getRequirementId() {
        return requirementId;
    }

    public RequirementKnowledgeModel getKnowledgeModel() {
        return knowledgeModel;
    }

    public String getAnalyzerVersion() {
        return analyzerVersion;
    }

    public Instant getAnalyzedAt() {
        return analyzedAt;
    }
}
