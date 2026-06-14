package com.engineeringproductivity.platform.requirement.analysis.api;

import com.engineeringproductivity.platform.requirement.analysis.domain.RequirementAnalysis;
import com.engineeringproductivity.platform.requirement.analysis.domain.model.AnalyzedApiEndpoint;
import com.engineeringproductivity.platform.requirement.analysis.domain.model.AnalyzedEdgeCase;
import com.engineeringproductivity.platform.requirement.analysis.domain.model.AnalyzedEntity;
import com.engineeringproductivity.platform.requirement.analysis.domain.model.AnalyzedFunctionalRequirement;
import com.engineeringproductivity.platform.requirement.analysis.domain.model.AnalyzedSecurityRequirement;
import com.engineeringproductivity.platform.requirement.analysis.domain.model.AnalyzedValidationRule;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RequirementAnalysisResponse(
        UUID analysisId,
        UUID requirementId,
        String analyzerVersion,
        Instant analyzedAt,
        String requirementType,
        List<AnalyzedEntity> entities,
        List<AnalyzedApiEndpoint> apiEndpoints,
        List<AnalyzedValidationRule> validationRules,
        List<AnalyzedFunctionalRequirement> functionalRequirements,
        List<AnalyzedSecurityRequirement> securityRequirements,
        List<AnalyzedEdgeCase> edgeCases
) {
    public static RequirementAnalysisResponse from(RequirementAnalysis analysis) {
        var model = analysis.getKnowledgeModel();
        return new RequirementAnalysisResponse(
                analysis.getId(),
                analysis.getRequirementId(),
                analysis.getAnalyzerVersion(),
                analysis.getAnalyzedAt(),
                model.resolvedType().name(),
                model.entities(),
                model.apiEndpoints(),
                model.validationRules(),
                model.functionalRequirements(),
                model.securityRequirements(),
                model.edgeCases()
        );
    }
}
