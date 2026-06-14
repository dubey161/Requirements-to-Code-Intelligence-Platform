package com.engineeringproductivity.platform.requirement.api;

import com.engineeringproductivity.platform.requirement.domain.RequirementStatus;
import com.engineeringproductivity.platform.requirement.domain.RequirementStory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RequirementResponse(
        UUID id,
        String externalKey,
        String title,
        String description,
        List<String> acceptanceCriteria,
        RequirementStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public static RequirementResponse from(RequirementStory story) {
        return new RequirementResponse(
                story.getId(),
                story.getExternalKey(),
                story.getTitle(),
                story.getDescription(),
                story.getAcceptanceCriteria(),
                story.getStatus(),
                story.getCreatedAt(),
                story.getUpdatedAt()
        );
    }
}

