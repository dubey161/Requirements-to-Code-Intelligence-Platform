package com.engineeringproductivity.platform.requirement.domain;

public enum RequirementStatus {
    RECEIVED,
    ANALYSIS_PENDING,
    ANALYZED,
    ANALYSIS_FAILED;

    public boolean isTerminal() {
        return this == ANALYZED || this == ANALYSIS_FAILED;
    }
}

