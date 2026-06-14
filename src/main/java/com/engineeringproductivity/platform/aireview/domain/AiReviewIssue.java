package com.engineeringproductivity.platform.aireview.domain;

public record AiReviewIssue(
        String category,    // SECURITY | VALIDATION | CODE_SMELL | ARCHITECTURE | PERFORMANCE | REQUIREMENT_GAP
        String severity,    // CRITICAL | HIGH | MEDIUM | LOW
        String description,
        String suggestion,
        String location     // file/method if identifiable
) {}
