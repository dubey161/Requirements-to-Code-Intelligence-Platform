package com.engineeringproductivity.platform.compliance.domain;

public record ComplianceGap(
        GapCategory category,
        Severity severity,
        String description,
        String expected,
        String found
) {
    public enum GapCategory {
        MISSING_ENTITY,
        MISSING_ENDPOINT,
        MISSING_VALIDATION,
        MISSING_SECURITY,
        MISSING_EDGE_CASE_HANDLING,
        MISSING_FUNCTIONAL_REQUIREMENT
    }

    public enum Severity {
        CRITICAL,   // blocks merge
        HIGH,       // must fix before merge
        MEDIUM,     // should fix
        LOW         // informational
    }
}
