package com.engineeringproductivity.platform.requirement.analysis.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RequirementAnalysisRepository extends JpaRepository<RequirementAnalysis, UUID> {

    Optional<RequirementAnalysis> findByRequirementId(UUID requirementId);

    boolean existsByRequirementId(UUID requirementId);
}
