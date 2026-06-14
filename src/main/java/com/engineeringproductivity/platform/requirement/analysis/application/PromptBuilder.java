package com.engineeringproductivity.platform.requirement.analysis.application;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Builds LLM prompts that produce structured JSON output matching RequirementKnowledgeModel.
 * The system prompt instructs the model on the schema; the user prompt provides the requirement text.
 */
@Component
public class PromptBuilder {

    public static final String SYSTEM_PROMPT = """
            You are an expert software requirements analyst and backend architect.

            Your job is to analyze a software requirement story and extract structured information.

            You MUST return ONLY valid JSON. No explanation, no markdown, no code fences.
            The JSON must exactly match the schema shown in the user message.

            Extraction rules:
            - entities: identify domain objects that will become database tables and Java classes.
              For each entity infer fields (name, inferredType, required, constraints).
              inferredType must be one of: UUID, String, Integer, Long, BigDecimal, Boolean, Instant, Enum.
              relationships describe how entities relate (e.g. "Order belongs to User").
            - apiEndpoints: identify REST operations. httpMethod must be GET/POST/PUT/PATCH/DELETE.
              suggestedPath follows REST conventions (/api/v1/resources/{id}).
            - validationRules: extract input constraints. ruleType must be one of:
              NOT_NULL, MIN_LENGTH, MAX_LENGTH, FORMAT, RANGE, UNIQUE, PATTERN, POSITIVE, ENUM_VALUE.
              Use PATTERN when the requirement says a field must not contain spaces, must not contain special
              characters, must be alphanumeric, or must match a specific character format.
              For PATTERN rules always include the regex in the description, e.g.:
              "Must match ^[a-zA-Z0-9]+$ (no spaces or special characters)"
            - functionalRequirements: number them FR-001, FR-002... Priority must be MUST/SHOULD/MAY.
              Acceptance criteria each become a separate FR. Additional behaviors from description too.
            - securityRequirements: category must be one of:
              AUTHENTICATION, AUTHORIZATION, DATA_PROTECTION, RATE_LIMITING, INPUT_SANITIZATION.
              Only include if the requirement explicitly or implicitly mentions security concerns.
            - edgeCases: error conditions, boundary cases, failure scenarios, concurrency concerns.
              Include standard REST error cases (not found, conflict, invalid input) if relevant.

            Be thorough. A well-specified requirement should yield 2-5 entities, 3-8 endpoints,
            3-10 validation rules, all acceptance criteria as FRs, and at least 3 edge cases.
            """;

    public PromptInput build(String title, String description, List<String> acceptanceCriteria) {
        return build(title, description, acceptanceCriteria, null);
    }

    public PromptInput build(String title, String description, List<String> acceptanceCriteria,
                              String requirementType) {
        String userPrompt = buildUserPrompt(title, description, acceptanceCriteria, requirementType);
        return new PromptInput(SYSTEM_PROMPT, userPrompt);
    }

    private String buildUserPrompt(String title, String description, List<String> acceptanceCriteria,
                                    String requirementType) {
        String criteriaText = IntStream.range(0, acceptanceCriteria.size())
                .mapToObj(i -> "  %d. %s".formatted(i + 1, acceptanceCriteria.get(i)))
                .collect(Collectors.joining("\n"));

        String typeInstruction = buildTypeInstruction(requirementType);

        return """
                Analyze the following software requirement story and return a JSON object matching the schema below.

                %s

                === REQUIREMENT ===
                Title: %s

                Description:
                %s

                Acceptance Criteria:
                %s

                === REQUIRED JSON SCHEMA ===
                {
                  "entities": [
                    {
                      "name": "EntityName",
                      "description": "what this entity represents",
                      "fields": [
                        {
                          "name": "fieldName",
                          "inferredType": "String",
                          "required": true,
                          "constraints": ["NOT_BLANK", "MAX_255"]
                        }
                      ],
                      "relationships": ["EntityName belongs to OtherEntity"]
                    }
                  ],
                  "apiEndpoints": [
                    {
                      "httpMethod": "POST",
                      "suggestedPath": "/api/v1/resources",
                      "description": "what this endpoint does",
                      "requestEntity": "CreateResourceRequest",
                      "responseEntity": "ResourceResponse"
                    }
                  ],
                  "validationRules": [
                    {
                      "targetField": "email",
                      "ruleType": "FORMAT",
                      "description": "email must be a valid email address",
                      "extractedFrom": "the text that triggered this rule"
                    }
                  ],
                  "functionalRequirements": [
                    {
                      "id": "FR-001",
                      "description": "full statement of the requirement",
                      "priority": "MUST",
                      "sourceContext": "acceptance-criteria or description"
                    }
                  ],
                  "securityRequirements": [
                    {
                      "category": "AUTHENTICATION",
                      "description": "what the security concern is"
                    }
                  ],
                  "edgeCases": [
                    {
                      "condition": "what triggers this case",
                      "expectedBehavior": "what the system should do",
                      "sourceContext": "where in the requirement this came from"
                    }
                  ]
                }

                Return ONLY the JSON. Nothing else.
                """.formatted(typeInstruction, title, description, criteriaText.isEmpty() ? "  (none provided)" : criteriaText);
    }

    /**
     * Returns type-specific instructions injected into the user prompt.
     * These constrain the LLM to extract only what's relevant for this type.
     */
    private static String buildTypeInstruction(String requirementType) {
        if (requirementType == null) return "";
        return switch (requirementType) {
            case "ALGORITHM" -> """
                    === IMPORTANT: REQUIREMENT TYPE = ALGORITHM ===
                    This is an algorithmic coding problem, NOT a business CRUD application.
                    DO NOT extract database entities, repositories, REST endpoints, or Spring annotations.
                    DO extract:
                    - functionalRequirements: what the algorithm must compute (input, output, constraints, examples)
                    - edgeCases: null input, empty array, overflow, edge values
                    - validationRules: input validation (non-null array, non-empty, valid range)
                    Leave entities=[], apiEndpoints=[], securityRequirements=[].
                    """;
            case "MICROSERVICE" -> """
                    === IMPORTANT: REQUIREMENT TYPE = MICROSERVICE / EVENT-DRIVEN ===
                    This is a Kafka/messaging microservice, NOT a standard CRUD API.
                    DO NOT generate @RestController or @GetMapping endpoints unless explicitly stated.
                    Focus entities on message payload models, not JPA database entities.
                    Extract Kafka topic names as apiEndpoints with httpMethod="EVENT".
                    """;
            case "BATCH_JOB" -> """
                    === IMPORTANT: REQUIREMENT TYPE = BATCH_JOB ===
                    This is a scheduled batch process.
                    Focus on the input source (file/DB), processing logic, and output.
                    Do not generate REST endpoints unless explicitly required.
                    """;
            default -> "";
        };
    }

    public record PromptInput(String systemPrompt, String userPrompt) {}
}
