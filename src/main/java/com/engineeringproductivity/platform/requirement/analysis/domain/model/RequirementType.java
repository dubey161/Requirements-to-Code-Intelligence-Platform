package com.engineeringproductivity.platform.requirement.analysis.domain.model;

/**
 * Classifies a requirement into a generation category.
 * The classifier determines this BEFORE any code is generated,
 * so the right generator strategy is selected.
 */
public enum RequirementType {

    /** LeetCode-style problems, "Write a Java program", Input/Output/Constraints format. */
    ALGORITHM,

    /** Standard JPA Entity + Repository + Service + Controller CRUD application. */
    SPRING_CRUD,

    /** REST API with specific non-CRUD operations (search, aggregation, workflow). */
    REST_API,

    /** Kafka / RabbitMQ / event-driven consumer + producer. */
    MICROSERVICE,

    /** Scheduled batch, CSV import/export, ETL pipeline. */
    BATCH_JOB,

    /** Standalone utility class or library — no main(), no HTTP. */
    LIBRARY,

    /** Command-line tool with main() and args[] parsing. */
    CLI_APPLICATION
}
