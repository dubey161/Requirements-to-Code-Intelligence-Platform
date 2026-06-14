package com.engineeringproductivity.platform.requirement.analysis.domain.model;

public record AnalyzedApiEndpoint(
        String httpMethod,
        String suggestedPath,
        String description,
        String requestEntity,
        String responseEntity
) {}
