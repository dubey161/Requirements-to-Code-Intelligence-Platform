package com.engineeringproductivity.platform.codegen.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GeneratedCodeBundleRepository extends JpaRepository<GeneratedCodeBundle, UUID> {
    Optional<GeneratedCodeBundle> findByRequirementId(UUID requirementId);
}
