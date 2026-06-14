package com.engineeringproductivity.platform.risk.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RiskScoreRepository extends JpaRepository<RiskScore, UUID> {
    Optional<RiskScore> findByRequirementId(UUID requirementId);

    // Dashboard: risk scores for requirements created by a specific user
    @Query(value = "SELECT rs.* FROM risk_score rs " +
                   "JOIN requirement_story story ON rs.requirement_id = story.id " +
                   "WHERE story.created_by = :createdBy", nativeQuery = true)
    List<RiskScore> findByRequirementCreatedBy(@Param("createdBy") UUID createdBy);
}
