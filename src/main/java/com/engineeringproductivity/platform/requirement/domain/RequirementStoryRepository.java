package com.engineeringproductivity.platform.requirement.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RequirementStoryRepository extends JpaRepository<RequirementStory, UUID> {

    Optional<RequirementStory> findByExternalKey(String externalKey);

    boolean existsByExternalKey(String externalKey);

    // Dashboard queries
    long countByCreatedBy(UUID createdBy);

    long countByStatus(RequirementStatus status);

    List<RequirementStory> findTop20ByCreatedByOrderByCreatedAtDesc(UUID createdBy);
}
