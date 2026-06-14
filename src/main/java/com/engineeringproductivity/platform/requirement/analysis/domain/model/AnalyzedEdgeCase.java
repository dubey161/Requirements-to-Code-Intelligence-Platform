package com.engineeringproductivity.platform.requirement.analysis.domain.model;

public record AnalyzedEdgeCase(
        String condition,
        String expectedBehavior,
        String sourceContext
) {}
