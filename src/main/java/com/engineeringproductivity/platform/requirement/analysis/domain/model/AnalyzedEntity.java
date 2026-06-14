package com.engineeringproductivity.platform.requirement.analysis.domain.model;

import java.util.List;

public record AnalyzedEntity(
        String name,
        String description,
        List<EntityField> fields,
        List<String> relationships
) {}
