package com.engineeringproductivity.platform.requirement.analysis.domain.model;

public record AnalyzedFunctionalRequirement(
        String id,
        String description,
        String priority,
        String sourceContext
) {}
