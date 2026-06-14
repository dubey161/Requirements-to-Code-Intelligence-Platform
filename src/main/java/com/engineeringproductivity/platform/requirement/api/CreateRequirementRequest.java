package com.engineeringproductivity.platform.requirement.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateRequirementRequest(
        @NotBlank @Size(max = 100) String externalKey,
        @NotBlank @Size(max = 300) String title,
        @NotBlank String description,
        @NotEmpty List<@NotBlank String> acceptanceCriteria
) {
}

