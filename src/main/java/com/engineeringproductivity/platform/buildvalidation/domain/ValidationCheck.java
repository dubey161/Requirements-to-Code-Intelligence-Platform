package com.engineeringproductivity.platform.buildvalidation.domain;

/**
 * A single validation check result within a BuildValidationReport.
 */
public record ValidationCheck(
        String name,
        String status,   // PASS | WARN | FAIL
        String detail
) {}
