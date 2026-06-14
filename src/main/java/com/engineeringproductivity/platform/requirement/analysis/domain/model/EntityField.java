package com.engineeringproductivity.platform.requirement.analysis.domain.model;

import java.util.List;

public record EntityField(
        String name,
        String inferredType,
        boolean required,
        List<String> constraints
) {}
