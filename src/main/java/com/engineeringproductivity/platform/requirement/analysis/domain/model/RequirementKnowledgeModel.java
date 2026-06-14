package com.engineeringproductivity.platform.requirement.analysis.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * The structured knowledge extracted from a RequirementStory.
 * This is the central model that all downstream phases (code generation,
 * compliance engine, risk scoring) consume.
 *
 * requirementType and generationPlan are nullable for backward compatibility
 * with existing stored JSON that pre-dates the classifier phase.
 * All consumers treat null requirementType as SPRING_CRUD.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RequirementKnowledgeModel(
        List<AnalyzedEntity> entities,
        List<AnalyzedApiEndpoint> apiEndpoints,
        List<AnalyzedValidationRule> validationRules,
        List<AnalyzedFunctionalRequirement> functionalRequirements,
        List<AnalyzedSecurityRequirement> securityRequirements,
        List<AnalyzedEdgeCase> edgeCases,

        // ── Classifier output (null = SPRING_CRUD, backward-compatible) ──────
        String requirementType,
        GenerationPlan generationPlan
) {
    /** Resolved type — never null, defaults to SPRING_CRUD for legacy records. */
    public RequirementType resolvedType() {
        if (requirementType == null) return RequirementType.SPRING_CRUD;
        try { return RequirementType.valueOf(requirementType); }
        catch (IllegalArgumentException e) { return RequirementType.SPRING_CRUD; }
    }

    public static RequirementKnowledgeModel empty() {
        return new RequirementKnowledgeModel(
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                null, null
        );
    }

    /** Backward-compatible constructor for code that doesn't supply type/plan yet. */
    public static RequirementKnowledgeModel of(
            List<AnalyzedEntity> entities,
            List<AnalyzedApiEndpoint> apiEndpoints,
            List<AnalyzedValidationRule> validationRules,
            List<AnalyzedFunctionalRequirement> functionalRequirements,
            List<AnalyzedSecurityRequirement> securityRequirements,
            List<AnalyzedEdgeCase> edgeCases) {
        return new RequirementKnowledgeModel(
                entities, apiEndpoints, validationRules,
                functionalRequirements, securityRequirements, edgeCases,
                null, null);
    }
}
