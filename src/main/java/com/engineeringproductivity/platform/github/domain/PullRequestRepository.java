package com.engineeringproductivity.platform.github.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PullRequestRepository extends JpaRepository<CreatedPullRequest, UUID> {
    Optional<CreatedPullRequest> findByRequirementId(UUID requirementId);

    // Dashboard: count PRs belonging to requirements created by a specific user
    @Query(value = "SELECT COUNT(*) FROM pull_request pr " +
                   "JOIN requirement_story rs ON pr.requirement_id = rs.id " +
                   "WHERE rs.created_by = :createdBy", nativeQuery = true)
    long countByRequirementCreatedBy(@Param("createdBy") UUID createdBy);
}
