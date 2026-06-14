package com.engineeringproductivity.platform.compliance.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComplianceReportRepository extends JpaRepository<ComplianceReport, UUID> {
    Optional<ComplianceReport> findByRequirementId(UUID requirementId);

    // Dashboard: compliance reports for requirements created by a specific user
    @Query(value = "SELECT cr.* FROM compliance_report cr " +
                   "JOIN requirement_story rs ON cr.requirement_id = rs.id " +
                   "WHERE rs.created_by = :createdBy", nativeQuery = true)
    List<ComplianceReport> findByRequirementCreatedBy(@Param("createdBy") UUID createdBy);
}
