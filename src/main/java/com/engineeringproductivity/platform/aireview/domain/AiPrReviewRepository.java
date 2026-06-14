package com.engineeringproductivity.platform.aireview.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiPrReviewRepository extends JpaRepository<AiPrReview, UUID> {
    // Latest review per requirement
    Optional<AiPrReview> findTopByRequirementIdOrderByReviewedAtDesc(UUID requirementId);
    // Full history — model upgrades over time
    List<AiPrReview> findAllByRequirementIdOrderByReviewedAtDesc(UUID requirementId);
}
