package com.engineeringproductivity.platform.requirement.analysis.domain.model;

import java.util.List;

/**
 * The planner's output — tells the generator exactly what to produce
 * and what is forbidden for a given RequirementType.
 *
 * This prevents the "CRUD generator for everything" anti-pattern.
 */
public record GenerationPlan(
        /** Files that MUST be created, e.g. ["WeightedWordMapping.java"] */
        List<String> expectedArtifacts,

        /** Language/code constructs that MUST appear, e.g. ["main method", "solve method"] */
        List<String> mustHave,

        /** Constructs that MUST NOT appear, e.g. ["@Entity", "Repository", "Controller"] */
        List<String> mustNotHave,

        /** Plain-English description of what this plan generates. */
        String description
) {
    public static GenerationPlan empty() {
        return new GenerationPlan(List.of(), List.of(), List.of(), "");
    }

    /** Standard CRUD plan used as default for SPRING_CRUD requirements. */
    public static GenerationPlan forCrud(String entityName) {
        return new GenerationPlan(
                List.of(
                        entityName + ".java",
                        entityName + "Repository.java",
                        entityName + "Service.java",
                        entityName + "Controller.java",
                        "Create" + entityName + "Request.java",
                        entityName + "Response.java"
                ),
                List.of("@Entity", "@Repository", "@Service", "@RestController", "JpaRepository"),
                List.of(),
                "Standard Spring Boot CRUD for entity: " + entityName
        );
    }

    /** Algorithm plan — single class, main method, no Spring artefacts. */
    public static GenerationPlan forAlgorithm(String className) {
        return new GenerationPlan(
                List.of(className + ".java"),
                List.of("main method", "solve method", "time complexity comment"),
                List.of("@Entity", "@Repository", "@RestController", "@Service",
                        "JpaRepository", "extends JpaRepository", "DataSource"),
                "Standalone algorithm class: " + className
        );
    }
}
