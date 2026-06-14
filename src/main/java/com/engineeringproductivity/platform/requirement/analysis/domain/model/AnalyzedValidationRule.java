package com.engineeringproductivity.platform.requirement.analysis.domain.model;

public record AnalyzedValidationRule(
        String targetField,
        String ruleType,
        String description,
        String extractedFrom
) {}
