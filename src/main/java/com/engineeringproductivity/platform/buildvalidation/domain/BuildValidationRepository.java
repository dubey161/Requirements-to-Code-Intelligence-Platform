package com.engineeringproductivity.platform.buildvalidation.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BuildValidationRepository extends JpaRepository<BuildValidationReport, UUID> {
    Optional<BuildValidationReport> findByRequirementId(UUID requirementId);
}
