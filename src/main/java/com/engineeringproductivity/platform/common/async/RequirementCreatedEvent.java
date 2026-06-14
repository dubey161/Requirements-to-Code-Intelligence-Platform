package com.engineeringproductivity.platform.common.async;

import java.util.UUID;

/**
 * Published after a RequirementStory is persisted.
 * Consumed asynchronously to trigger analysis without blocking the HTTP response.
 */
public record RequirementCreatedEvent(UUID requirementId, String externalKey) {}
