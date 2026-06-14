package com.engineeringproductivity.platform.requirement.analysis.application;

import com.engineeringproductivity.platform.requirement.analysis.domain.model.AnalyzedApiEndpoint;
import com.engineeringproductivity.platform.requirement.analysis.domain.model.AnalyzedEdgeCase;
import com.engineeringproductivity.platform.requirement.analysis.domain.model.AnalyzedEntity;
import com.engineeringproductivity.platform.requirement.analysis.domain.model.AnalyzedFunctionalRequirement;
import com.engineeringproductivity.platform.requirement.analysis.domain.model.AnalyzedSecurityRequirement;
import com.engineeringproductivity.platform.requirement.analysis.domain.model.AnalyzedValidationRule;
import com.engineeringproductivity.platform.requirement.analysis.domain.model.EntityField;
import com.engineeringproductivity.platform.requirement.analysis.domain.model.RequirementKnowledgeModel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Rule-based extractor that parses free-text requirement stories into a
 * structured RequirementKnowledgeModel. Uses regex patterns and linguistic
 * heuristics. No external dependencies — always available as a fallback.
 */
public class RuleBasedRequirementAnalyzer implements RequirementAnalyzer {

    private static final String VERSION = "rule-based-v1";

    @Override
    public String version() {
        return VERSION;
    }

    // --- Entity extraction patterns ---
    private static final Pattern CRUD_ENTITY = Pattern.compile(
            "(?:create|add|register|store|save|update|edit|modify|delete|remove|cancel|" +
            "retrieve|get|fetch|view|list|search|manage|handle|process)\\s+" +
            "(?:a|an|the|new)?\\s*([A-Z][a-zA-Z]+(?:\\s+[A-Z][a-zA-Z]+)*)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern CAPITALIZED_NOUN = Pattern.compile("\\b([A-Z][a-z]{2,})\\b");

    private static final Set<String> ENTITY_STOP_WORDS = Set.of(
            "The", "This", "That", "When", "Where", "With", "From", "Into",
            "Upon", "Each", "Every", "Must", "Should", "Shall", "Will", "Can",
            "User", "System", "Api", "Http", "Json", "Rest", "Jwt", "Sql"
    );

    // --- API operation patterns ---
    private static final List<OperationPattern> OPERATION_PATTERNS = List.of(
            new OperationPattern(Pattern.compile(
                    "(?:create|add|register|submit|post|new)\\s+(?:a|an|new)?\\s*([a-zA-Z]+(?:\\s+[a-zA-Z]+)?)",
                    Pattern.CASE_INSENSITIVE), "POST"),
            new OperationPattern(Pattern.compile(
                    "(?:list|search|find all|retrieve all|browse|filter|query)\\s+([a-zA-Z]+(?:\\s+[a-zA-Z]+)?)",
                    Pattern.CASE_INSENSITIVE), "GET_COLLECTION"),
            new OperationPattern(Pattern.compile(
                    "(?:get|retrieve|fetch|view|read|look up|show)\\s+(?:a|an|the)?\\s*([a-zA-Z]+(?:\\s+[a-zA-Z]+)?)",
                    Pattern.CASE_INSENSITIVE), "GET"),
            new OperationPattern(Pattern.compile(
                    "(?:update|edit|modify|change|set|patch)\\s+(?:a|an|the)?\\s*([a-zA-Z]+(?:\\s+[a-zA-Z]+)?)",
                    Pattern.CASE_INSENSITIVE), "PUT"),
            new OperationPattern(Pattern.compile(
                    "(?:delete|remove|cancel|deactivate|disable|archive)\\s+(?:a|an|the)?\\s*([a-zA-Z]+(?:\\s+[a-zA-Z]+)?)",
                    Pattern.CASE_INSENSITIVE), "DELETE")
    );

    // --- Validation patterns ---
    private static final Pattern REQUIRED_FIELD = Pattern.compile(
            "([a-zA-Z][a-zA-Z\\s]{1,30})\\s+(?:is|are)?\\s*(?:required|mandatory|must not be (?:empty|blank|null))",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern MAX_LENGTH = Pattern.compile(
            "([a-zA-Z][a-zA-Z\\s]{1,30})\\s+(?:must|should|cannot|can't).*?(?:exceed|more than|longer than|at most)\\s+(\\d+)\\s*(?:characters|chars)?",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern MIN_LENGTH = Pattern.compile(
            "([a-zA-Z][a-zA-Z\\s]{1,30})\\s+(?:must|should).*?(?:at least|minimum of|minimum)\\s+(\\d+)\\s*(?:characters|chars)?",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern FORMAT_RULE = Pattern.compile(
            "([a-zA-Z][a-zA-Z\\s]{1,30})\\s+(?:must|should)\\s+(?:be a valid|match|follow|conform to)\\s+([a-zA-Z\\s]+?)(?:\\.|,|$)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern RANGE_RULE = Pattern.compile(
            "([a-zA-Z][a-zA-Z\\s]{1,30})\\s+(?:must|should).*?(?:between|from)\\s+(\\d+)\\s*(?:and|to)\\s*(\\d+)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern NO_SPECIAL_CHARS = Pattern.compile(
            "([a-zA-Z][a-zA-Z\\s]{1,30})\\s+(?:should not|must not|cannot|should not)\\s+contain\\s+(?:space[s]?|special\\s*character[s]?|special\\s*char[s]?)[^.]*",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern UNIQUE_RULE = Pattern.compile(
            "([a-zA-Z][a-zA-Z\\s]{1,30})\\s+(?:must be|should be|is)\\s+unique",
            Pattern.CASE_INSENSITIVE
    );

    // --- Security patterns ---
    private static final Map<String, Pattern> SECURITY_PATTERNS = Map.of(
            "AUTHENTICATION", Pattern.compile(
                    "(?:authenticated|logged in|log in|sign in|login|token|JWT|OAuth|session)",
                    Pattern.CASE_INSENSITIVE),
            "AUTHORIZATION", Pattern.compile(
                    "(?:authorized|permission|role|access control|privilege|admin|only|restricted)",
                    Pattern.CASE_INSENSITIVE),
            "DATA_PROTECTION", Pattern.compile(
                    "(?:encrypt|hash|bcrypt|SHA|sensitive|PII|personal data|password|secret|confidential)",
                    Pattern.CASE_INSENSITIVE),
            "RATE_LIMITING", Pattern.compile(
                    "(?:rate limit|throttle|too many requests|brute force|attempt)",
                    Pattern.CASE_INSENSITIVE),
            "INPUT_SANITIZATION", Pattern.compile(
                    "(?:inject|XSS|sanitize|escape|SQL injection|script|malicious)",
                    Pattern.CASE_INSENSITIVE)
    );

    // --- Edge case patterns ---
    private static final Pattern EDGE_CASE_IF = Pattern.compile(
            "(?:if|when|in case|in the case|should|unless)\\s+([^,.;]{10,80})[,.]?\\s*(?:then|,)?\\s*([^.;]{5,80})",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern ALREADY_EXISTS = Pattern.compile(
            "(?:already exists|duplicate|conflict)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern NOT_FOUND = Pattern.compile(
            "(?:not found|does not exist|no longer exists|missing)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern INVALID_INPUT = Pattern.compile(
            "(?:invalid|incorrect|malformed|wrong format|bad request)",
            Pattern.CASE_INSENSITIVE
    );

    public RequirementKnowledgeModel analyze(
            String title,
            String description,
            List<String> acceptanceCriteria
    ) {
        String fullText = buildFullText(title, description, acceptanceCriteria);

        List<AnalyzedEntity> entities = extractEntities(fullText, title);
        List<AnalyzedApiEndpoint> apiEndpoints = extractApiEndpoints(fullText, entities);
        List<AnalyzedValidationRule> validationRules = extractValidationRules(fullText);
        List<AnalyzedFunctionalRequirement> functionalRequirements =
                extractFunctionalRequirements(description, acceptanceCriteria);
        List<AnalyzedSecurityRequirement> securityRequirements = extractSecurityRequirements(fullText);
        List<AnalyzedEdgeCase> edgeCases = extractEdgeCases(fullText);

        return RequirementKnowledgeModel.of(
                entities,
                apiEndpoints,
                validationRules,
                functionalRequirements,
                securityRequirements,
                edgeCases
        );
    }

    // -------------------------------------------------------------------------
    // Entity extraction
    // -------------------------------------------------------------------------

    private List<AnalyzedEntity> extractEntities(String fullText, String title) {
        Map<String, Integer> candidateFrequency = new LinkedHashMap<>();

        // Extract from CRUD verb patterns
        Matcher crudMatcher = CRUD_ENTITY.matcher(fullText);
        while (crudMatcher.find()) {
            String candidate = normalizeEntityName(crudMatcher.group(1));
            if (isValidEntityCandidate(candidate)) {
                candidateFrequency.merge(candidate, 2, Integer::sum); // CRUD match = 2 weight
            }
        }

        // Extract capitalized nouns and count frequency
        Matcher capMatcher = CAPITALIZED_NOUN.matcher(fullText);
        while (capMatcher.find()) {
            String candidate = capMatcher.group(1);
            if (isValidEntityCandidate(candidate)) {
                candidateFrequency.merge(candidate, 1, Integer::sum);
            }
        }

        // Keep entities that appear with sufficient frequency or were CRUD-matched
        return candidateFrequency.entrySet().stream()
                .filter(e -> e.getValue() >= 2)
                .map(e -> buildEntity(e.getKey(), fullText))
                .collect(Collectors.toList());
    }

    private AnalyzedEntity buildEntity(String entityName, String fullText) {
        List<EntityField> fields = inferFields(entityName, fullText);
        List<String> relationships = inferRelationships(entityName, fullText);
        String description = "Domain entity identified from requirement text";
        return new AnalyzedEntity(entityName, description, fields, relationships);
    }

    private List<EntityField> inferFields(String entityName, String fullText) {
        List<EntityField> fields = new ArrayList<>();
        String lowerEntity = entityName.toLowerCase();
        String lowerText = fullText.toLowerCase();

        // Common field inference patterns: "the [entity]'s [field]" or "[entity] [field]"
        Pattern fieldPattern = Pattern.compile(
                lowerEntity + "'?s?\\s+([a-z][a-z_]{2,20})(?:\\s+(?:field|attribute|property))?",
                Pattern.CASE_INSENSITIVE
        );
        Set<String> seen = new LinkedHashSet<>();
        Matcher m = fieldPattern.matcher(lowerText);
        while (m.find() && fields.size() < 8) {
            String fieldName = m.group(1).toLowerCase();
            if (seen.add(fieldName) && !isStopWord(fieldName)) {
                fields.add(new EntityField(fieldName, inferFieldType(fieldName), true, List.of()));
            }
        }

        // Add standard fields inferred from entity name context
        if (lowerText.contains(lowerEntity) && fields.isEmpty()) {
            fields.add(new EntityField("id", "UUID", false, List.of("auto-generated")));
        }

        return fields;
    }

    private String inferFieldType(String fieldName) {
        if (fieldName.matches(".*(?:id|uuid|key).*")) return "UUID";
        if (fieldName.matches(".*(?:name|title|description|text|message|content|email|address|phone|url).*")) return "String";
        if (fieldName.matches(".*(?:count|number|quantity|amount|age|size|length|limit).*")) return "Integer";
        if (fieldName.matches(".*(?:price|cost|rate|fee|salary|balance|total).*")) return "BigDecimal";
        if (fieldName.matches(".*(?:date|time|at|on|created|updated|expires).*")) return "Instant";
        if (fieldName.matches(".*(?:enabled|active|verified|flag|visible|deleted).*")) return "Boolean";
        return "String";
    }

    private List<String> inferRelationships(String entityName, String fullText) {
        List<String> relationships = new ArrayList<>();
        String lower = fullText.toLowerCase();
        String entity = entityName.toLowerCase();

        Pattern hasMany = Pattern.compile(
                entity + "\\s+(?:has|contains|includes|holds|owns)\\s+(?:many|multiple|several|one or more)?\\s*([a-z]+)",
                Pattern.CASE_INSENSITIVE
        );
        Pattern belongsTo = Pattern.compile(
                entity + "\\s+(?:belongs to|is owned by|is part of|is assigned to)\\s+([a-z]+)",
                Pattern.CASE_INSENSITIVE
        );

        Matcher m = hasMany.matcher(lower);
        while (m.find()) {
            relationships.add(entityName + " has many " + capitalize(m.group(1)));
        }
        m = belongsTo.matcher(lower);
        while (m.find()) {
            relationships.add(entityName + " belongs to " + capitalize(m.group(1)));
        }
        return relationships;
    }

    private boolean isValidEntityCandidate(String candidate) {
        if (candidate == null || candidate.length() < 3 || candidate.length() > 40) return false;
        if (ENTITY_STOP_WORDS.contains(candidate)) return false;
        // Must look like a domain noun (not a verb, not all caps abbreviation)
        return candidate.matches("[A-Z][a-zA-Z]+") && !candidate.matches("[A-Z]{2,}");
    }

    private String normalizeEntityName(String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim();
        // Take only the first word if it's already Title Case
        String[] words = trimmed.split("\\s+");
        if (words.length > 0 && words[0].matches("[A-Z][a-z]+")) {
            return words[0];
        }
        return capitalize(words[0]);
    }

    // -------------------------------------------------------------------------
    // API endpoint extraction
    // -------------------------------------------------------------------------

    private List<AnalyzedApiEndpoint> extractApiEndpoints(String fullText, List<AnalyzedEntity> entities) {
        Set<String> seen = new LinkedHashSet<>();
        List<AnalyzedApiEndpoint> endpoints = new ArrayList<>();

        for (OperationPattern op : OPERATION_PATTERNS) {
            Matcher m = op.pattern().matcher(fullText);
            while (m.find()) {
                String resourceRaw = m.group(1).trim().toLowerCase();
                String resource = resourceRaw.replaceAll("\\s+", "-");
                String dedupeKey = op.httpMethod() + ":" + resource;
                if (seen.add(dedupeKey) && resource.length() >= 3 && resource.length() <= 30) {
                    endpoints.add(buildEndpoint(op.httpMethod(), resource, m.group(0)));
                }
            }
        }

        // Ensure entities without endpoints get a default CRUD set if they look like main resources
        for (AnalyzedEntity entity : entities) {
            String resource = entity.name().toLowerCase() + "s";
            if (seen.add("POST:" + resource)) {
                endpoints.add(buildEndpoint("POST", resource, "Create " + entity.name()));
            }
        }

        return endpoints;
    }

    private AnalyzedApiEndpoint buildEndpoint(String httpMethod, String resource, String context) {
        String singularResource = resource.endsWith("s") ? resource.substring(0, resource.length() - 1) : resource;
        return switch (httpMethod) {
            case "POST" -> new AnalyzedApiEndpoint(
                    "POST", "/api/v1/" + pluralize(resource), context,
                    capitalize(singularResource) + "Request", capitalize(singularResource) + "Response");
            case "GET_COLLECTION" -> new AnalyzedApiEndpoint(
                    "GET", "/api/v1/" + pluralize(resource), context,
                    null, "List<" + capitalize(singularResource) + "Response>");
            case "GET" -> new AnalyzedApiEndpoint(
                    "GET", "/api/v1/" + pluralize(resource) + "/{id}", context,
                    null, capitalize(singularResource) + "Response");
            case "PUT" -> new AnalyzedApiEndpoint(
                    "PUT", "/api/v1/" + pluralize(resource) + "/{id}", context,
                    capitalize(singularResource) + "UpdateRequest", capitalize(singularResource) + "Response");
            case "DELETE" -> new AnalyzedApiEndpoint(
                    "DELETE", "/api/v1/" + pluralize(resource) + "/{id}", context,
                    null, null);
            default -> new AnalyzedApiEndpoint(
                    "GET", "/api/v1/" + pluralize(resource), context, null, null);
        };
    }

    // -------------------------------------------------------------------------
    // Validation rule extraction
    // -------------------------------------------------------------------------

    private List<AnalyzedValidationRule> extractValidationRules(String fullText) {
        List<AnalyzedValidationRule> rules = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        extractPattern(REQUIRED_FIELD, fullText, m -> {
            String field = m.group(1).trim();
            String key = "NOT_NULL:" + field;
            if (seen.add(key)) {
                rules.add(new AnalyzedValidationRule(field, "NOT_NULL",
                        field + " is required and must not be null or blank", m.group(0)));
            }
        });

        extractPattern(MAX_LENGTH, fullText, m -> {
            String field = m.group(1).trim();
            String max = m.group(2);
            String key = "MAX_LENGTH:" + field;
            if (seen.add(key)) {
                rules.add(new AnalyzedValidationRule(field, "MAX_LENGTH",
                        field + " must not exceed " + max + " characters", m.group(0)));
            }
        });

        extractPattern(MIN_LENGTH, fullText, m -> {
            String field = m.group(1).trim();
            String min = m.group(2);
            String key = "MIN_LENGTH:" + field;
            if (seen.add(key)) {
                rules.add(new AnalyzedValidationRule(field, "MIN_LENGTH",
                        field + " must be at least " + min + " characters", m.group(0)));
            }
        });

        extractPattern(FORMAT_RULE, fullText, m -> {
            String field = m.group(1).trim();
            String format = m.group(2).trim();
            String key = "FORMAT:" + field;
            if (seen.add(key)) {
                rules.add(new AnalyzedValidationRule(field, "FORMAT",
                        field + " must be a valid " + format, m.group(0)));
            }
        });

        extractPattern(RANGE_RULE, fullText, m -> {
            String field = m.group(1).trim();
            String key = "RANGE:" + field;
            if (seen.add(key)) {
                rules.add(new AnalyzedValidationRule(field, "RANGE",
                        field + " must be between " + m.group(2) + " and " + m.group(3), m.group(0)));
            }
        });

        extractPattern(UNIQUE_RULE, fullText, m -> {
            String field = m.group(1).trim();
            String key = "UNIQUE:" + field;
            if (seen.add(key)) {
                rules.add(new AnalyzedValidationRule(field, "UNIQUE",
                        field + " must be unique across all records", m.group(0)));
            }
        });

        extractPattern(NO_SPECIAL_CHARS, fullText, m -> {
            String field = m.group(1).trim();
            String key = "PATTERN:" + field;
            if (seen.add(key)) {
                rules.add(new AnalyzedValidationRule(field, "PATTERN",
                        "Must match ^[a-zA-Z0-9]+$ (no spaces or special characters)", m.group(0)));
            }
        });

        // Also detect plain "not contain space" / "not contain special character" patterns
        java.util.regex.Matcher spaceM = Pattern.compile(
                "([a-zA-Z][a-zA-Z\\s]{1,30})\\s+(?:not|no)\\s+(?:contain\\s+)?(?:space[s]?|special\\s*character[s]?)",
                Pattern.CASE_INSENSITIVE).matcher(fullText);
        while (spaceM.find()) {
            String field = spaceM.group(1).trim();
            String key = "PATTERN:" + field;
            if (seen.add(key)) {
                rules.add(new AnalyzedValidationRule(field, "PATTERN",
                        "Must match ^[a-zA-Z0-9]+$ (no spaces or special characters)", spaceM.group(0)));
            }
        }

        return rules;
    }

    // -------------------------------------------------------------------------
    // Functional requirement extraction
    // -------------------------------------------------------------------------

    private List<AnalyzedFunctionalRequirement> extractFunctionalRequirements(
            String description, List<String> acceptanceCriteria) {

        List<AnalyzedFunctionalRequirement> requirements = new ArrayList<>();
        AtomicInteger counter = new AtomicInteger(1);

        // Each acceptance criterion is a functional requirement
        for (String criterion : acceptanceCriteria) {
            String priority = inferPriority(criterion);
            requirements.add(new AnalyzedFunctionalRequirement(
                    "FR-%03d".formatted(counter.getAndIncrement()),
                    criterion.trim(),
                    priority,
                    "acceptance-criteria"
            ));
        }

        // Extract "must/should/shall" statements from description
        Pattern mustShould = Pattern.compile(
                "(?:The system|System|It|The \\w+)?\\s*(?:must|shall|should)\\s+([^.;]{10,120})[.;]",
                Pattern.CASE_INSENSITIVE
        );
        Matcher m = mustShould.matcher(description);
        while (m.find()) {
            String stmt = m.group(0).trim();
            // Avoid duplicating what's already in acceptance criteria
            boolean alreadyCovered = acceptanceCriteria.stream()
                    .anyMatch(ac -> ac.toLowerCase().contains(m.group(1).substring(0, Math.min(20, m.group(1).length())).toLowerCase()));
            if (!alreadyCovered) {
                requirements.add(new AnalyzedFunctionalRequirement(
                        "FR-%03d".formatted(counter.getAndIncrement()),
                        stmt,
                        inferPriority(stmt),
                        "description"
                ));
            }
        }

        return requirements;
    }

    private String inferPriority(String text) {
        String lower = text.toLowerCase();
        if (lower.contains("must") || lower.contains("shall") || lower.contains("required")) return "MUST";
        if (lower.contains("should")) return "SHOULD";
        if (lower.contains("may") || lower.contains("can") || lower.contains("could")) return "MAY";
        return "SHOULD";
    }

    // -------------------------------------------------------------------------
    // Security requirement extraction
    // -------------------------------------------------------------------------

    private List<AnalyzedSecurityRequirement> extractSecurityRequirements(String fullText) {
        List<AnalyzedSecurityRequirement> requirements = new ArrayList<>();

        for (Map.Entry<String, Pattern> entry : SECURITY_PATTERNS.entrySet()) {
            Matcher m = entry.getValue().matcher(fullText);
            if (m.find()) {
                // Extract surrounding context (up to 120 chars) for the description
                int start = Math.max(0, m.start() - 30);
                int end = Math.min(fullText.length(), m.end() + 60);
                String context = fullText.substring(start, end).trim();
                requirements.add(new AnalyzedSecurityRequirement(entry.getKey(), context));
            }
        }

        return requirements;
    }

    // -------------------------------------------------------------------------
    // Edge case extraction
    // -------------------------------------------------------------------------

    private List<AnalyzedEdgeCase> extractEdgeCases(String fullText) {
        List<AnalyzedEdgeCase> edgeCases = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        // Conditional clauses
        Matcher m = EDGE_CASE_IF.matcher(fullText);
        while (m.find() && edgeCases.size() < 10) {
            String condition = m.group(1).trim();
            String behavior = m.group(2).trim();
            String key = condition.substring(0, Math.min(30, condition.length()));
            if (seen.add(key) && condition.length() >= 10) {
                edgeCases.add(new AnalyzedEdgeCase(condition, behavior, m.group(0)));
            }
        }

        // Already-exists pattern
        if (ALREADY_EXISTS.matcher(fullText).find()) {
            edgeCases.add(new AnalyzedEdgeCase(
                    "Resource already exists",
                    "Return 409 Conflict with descriptive error message",
                    "Detected 'already exists/duplicate/conflict' in requirements"
            ));
        }

        // Not-found pattern
        if (NOT_FOUND.matcher(fullText).find()) {
            edgeCases.add(new AnalyzedEdgeCase(
                    "Resource not found",
                    "Return 404 Not Found with descriptive error message",
                    "Detected 'not found/does not exist' in requirements"
            ));
        }

        // Invalid input pattern
        if (INVALID_INPUT.matcher(fullText).find()) {
            edgeCases.add(new AnalyzedEdgeCase(
                    "Invalid or malformed input",
                    "Return 400 Bad Request with field-level validation errors",
                    "Detected 'invalid/incorrect/malformed' in requirements"
            ));
        }

        return edgeCases;
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    private String buildFullText(String title, String description, List<String> acceptanceCriteria) {
        StringBuilder sb = new StringBuilder();
        sb.append(title).append(". ");
        sb.append(description).append(" ");
        acceptanceCriteria.forEach(ac -> sb.append(ac).append(" "));
        return sb.toString();
    }

    @FunctionalInterface
    private interface MatchConsumer {
        void accept(Matcher m);
    }

    private void extractPattern(Pattern pattern, String text, MatchConsumer consumer) {
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            consumer.accept(m);
        }
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private String pluralize(String noun) {
        if (noun.endsWith("s") || noun.endsWith("x") || noun.endsWith("z") ||
                noun.endsWith("ch") || noun.endsWith("sh")) {
            return noun + "es";
        }
        if (noun.endsWith("y") && noun.length() > 1 &&
                !isVowel(noun.charAt(noun.length() - 2))) {
            return noun.substring(0, noun.length() - 1) + "ies";
        }
        return noun.endsWith("s") ? noun : noun + "s";
    }

    private boolean isVowel(char c) {
        return "aeiouAEIOU".indexOf(c) >= 0;
    }

    private boolean isStopWord(String word) {
        return Arrays.asList("the", "a", "an", "is", "are", "was", "were",
                "be", "been", "have", "has", "had", "do", "does", "did",
                "will", "would", "shall", "should", "may", "might", "must",
                "can", "could", "not", "no", "nor", "and", "or", "but",
                "for", "with", "from", "to", "of", "in", "on", "at", "by",
                "user", "system", "api", "data", "value", "field").contains(word.toLowerCase());
    }

    private record OperationPattern(Pattern pattern, String httpMethod) {}
}
