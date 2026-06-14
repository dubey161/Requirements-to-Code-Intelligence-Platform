package com.engineeringproductivity.platform.codegen.application;

import com.engineeringproductivity.platform.codegen.domain.GeneratedFile;
import com.engineeringproductivity.platform.requirement.analysis.domain.model.AnalyzedApiEndpoint;
import com.engineeringproductivity.platform.requirement.analysis.domain.model.AnalyzedEntity;
import com.engineeringproductivity.platform.requirement.analysis.domain.model.AnalyzedValidationRule;
import com.engineeringproductivity.platform.requirement.analysis.domain.model.EntityField;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Generates production-quality Spring Boot 3 / Java 21 source code
 * from a RequirementKnowledgeModel entity.
 */
class JavaCodeTemplates {

    // Fields added explicitly by templates — never duplicate them from analysis model
    private static final java.util.Set<String> RESERVED_FIELDS =
            java.util.Set.of("id", "createdAt", "updatedAt", "version", "created_at", "updated_at");

    static GeneratedFile entity(AnalyzedEntity entity, String basePackage) {
        String pkg = basePackage + "." + lower(entity.name()) + ".domain";
        String className = capitalize(entity.name());

        // Filter out id/createdAt/updatedAt/version — the template already adds them
        List<EntityField> domainFields = entity.fields().stream()
                .filter(f -> !RESERVED_FIELDS.contains(f.name()))
                .toList();

        String fields = domainFields.stream()
                .map(f -> entityField(f))
                .collect(Collectors.joining("\n\n"));

        String getters = domainFields.stream()
                .map(f -> "    public %s get%s() { return %s; }".formatted(
                        javaType(f.inferredType()), capitalize(f.name()), f.name()))
                .collect(Collectors.joining("\n"));

        String constructorParams = domainFields.stream()
                .map(f -> "%s %s".formatted(javaType(f.inferredType()), f.name()))
                .collect(Collectors.joining(", "));

        String constructorBody = domainFields.stream()
                .map(f -> "        this.%s = %s;".formatted(f.name(), f.name()))
                .collect(Collectors.joining("\n"));

        String factoryParams = domainFields.stream()
                .filter(EntityField::required)
                .map(f -> "%s %s".formatted(javaType(f.inferredType()), f.name()))
                .collect(Collectors.joining(", "));

        String factoryArgs = "UUID.randomUUID(),\n                " +
                domainFields.stream()
                        .map(f -> f.required() ? f.name() : "null")
                        .collect(Collectors.joining(",\n                "));

        String content = """
                package %s;

                import jakarta.persistence.*;
                import java.time.Instant;
                import java.util.UUID;

                @Entity
                @Table(name = "%s")
                public class %s {

                    @Id
                    private UUID id;

                %s

                    @Column(name = "created_at", nullable = false, updatable = false)
                    private Instant createdAt;

                    @Column(name = "updated_at", nullable = false)
                    private Instant updatedAt;

                    @Version
                    private long version;

                    protected %s() {}

                    private %s(UUID id, %s, Instant createdAt) {
                        this.id = id;
                %s
                        this.createdAt = createdAt;
                        this.updatedAt = createdAt;
                    }

                    public static %s create(%s) {
                        return new %s(%s, Instant.now());
                    }

                    public UUID getId() { return id; }
                    public Instant getCreatedAt() { return createdAt; }
                    public Instant getUpdatedAt() { return updatedAt; }
                %s
                }
                """.formatted(
                pkg, toSnakeCase(entity.name()) + "s", className,
                fields,
                className, className, constructorParams, constructorBody,
                className, factoryParams, className, factoryArgs,
                getters
        );

        return new GeneratedFile(className + ".java", pkg, content, GeneratedFile.FileType.ENTITY);
    }

    static GeneratedFile repository(AnalyzedEntity entity, String basePackage) {
        String pkg = basePackage + "." + lower(entity.name()) + ".domain";
        String className = capitalize(entity.name());

        // Find unique fields to generate finder methods
        String finderMethods = entity.fields().stream()
                .filter(f -> f.constraints().contains("UNIQUE") || f.name().toLowerCase().contains("email")
                        || f.name().toLowerCase().contains("username") || f.name().toLowerCase().contains("key"))
                .map(f -> """
                    boolean existsBy%s(%s %s);
                    java.util.Optional<%s> findBy%s(%s %s);
                """.formatted(
                        capitalize(f.name()), javaType(f.inferredType()), f.name(),
                        className, capitalize(f.name()), javaType(f.inferredType()), f.name()
                ))
                .collect(Collectors.joining());

        String content = """
                package %s;

                import org.springframework.data.jpa.repository.JpaRepository;
                import java.util.UUID;

                public interface %sRepository extends JpaRepository<%s, UUID> {
                %s}
                """.formatted(pkg, className, className, finderMethods);

        return new GeneratedFile(className + "Repository.java", pkg, content, GeneratedFile.FileType.REPOSITORY);
    }

    static GeneratedFile createRequest(AnalyzedEntity entity, List<AnalyzedValidationRule> validationRules,
                                        String basePackage) {
        String pkg = basePackage + "." + lower(entity.name()) + ".api";
        String className = "Create" + capitalize(entity.name()) + "Request";

        String fields = entity.fields().stream()
                .filter(EntityField::required)
                .filter(f -> !RESERVED_FIELDS.contains(f.name()))
                .map(f -> {
                    String annotations = buildValidationAnnotations(f, validationRules);
                    return "        %s%s %s".formatted(annotations, javaType(f.inferredType()), f.name());
                })
                .collect(Collectors.joining(",\n"));

        String content = """
                package %s;

                import jakarta.validation.constraints.*;
                import java.util.UUID;

                public record %s(
                %s
                ) {}
                """.formatted(pkg, className, fields);

        return new GeneratedFile(className + ".java", pkg, content, GeneratedFile.FileType.CREATE_REQUEST);
    }

    static GeneratedFile response(AnalyzedEntity entity, String basePackage) {
        String pkg = basePackage + "." + lower(entity.name()) + ".api";
        String entityClass = capitalize(entity.name());
        String className = entityClass + "Response";

        // Filter out id/createdAt/updatedAt — the template adds them explicitly
        List<EntityField> responseFields = entity.fields().stream()
                .filter(f -> !RESERVED_FIELDS.contains(f.name()))
                .toList();

        String fields = responseFields.stream()
                .map(f -> "        %s %s".formatted(javaType(f.inferredType()), f.name()))
                .collect(Collectors.joining(",\n"));

        String fromBody = responseFields.stream()
                .map(f -> "                entity.get%s()".formatted(capitalize(f.name())))
                .collect(Collectors.joining(",\n"));

        String content = """
                package %s;

                import java.time.Instant;
                import java.util.UUID;

                public record %s(
                        UUID id,
                %s,
                        Instant createdAt,
                        Instant updatedAt
                ) {
                    public static %s from(%s entity) {
                        return new %s(
                                entity.getId(),
                %s,
                                entity.getCreatedAt(),
                                entity.getUpdatedAt()
                        );
                    }
                }
                """.formatted(pkg, className, fields, className, entityClass, className, fromBody);

        return new GeneratedFile(className + ".java", pkg, content, GeneratedFile.FileType.RESPONSE);
    }

    static GeneratedFile service(AnalyzedEntity entity, String basePackage) {
        String pkg = basePackage + "." + lower(entity.name()) + ".application";
        String entityClass = capitalize(entity.name());
        String className = entityClass + "Service";
        String repoClass = entityClass + "Repository";
        String domainPkg = basePackage + "." + lower(entity.name()) + ".domain";
        String apiPkg = basePackage + "." + lower(entity.name()) + ".api";

        String createArgs = entity.fields().stream()
                .filter(EntityField::required)
                .filter(f -> !RESERVED_FIELDS.contains(f.name()))
                .map(f -> "request.%s()".formatted(f.name()))
                .collect(Collectors.joining(", "));

        String content = """
                package %s;

                import %s.%s;
                import %s.%s;
                import %s.Create%sRequest;
                import %s.%sResponse;
                import org.springframework.stereotype.Service;
                import org.springframework.transaction.annotation.Transactional;
                import java.util.List;
                import java.util.UUID;

                @Service
                public class %s {

                    private final %s repository;

                    public %s(%s repository) {
                        this.repository = repository;
                    }

                    @Transactional
                    public %s create(Create%sRequest request) {
                        return repository.save(%s.create(%s));
                    }

                    @Transactional(readOnly = true)
                    public %s get(UUID id) {
                        return repository.findById(id)
                                .orElseThrow(() -> new RuntimeException("%s not found: " + id));
                    }

                    @Transactional(readOnly = true)
                    public List<%s> list() {
                        return repository.findAll();
                    }

                    @Transactional
                    public void delete(UUID id) {
                        if (!repository.existsById(id)) {
                            throw new RuntimeException("%s not found: " + id);
                        }
                        repository.deleteById(id);
                    }
                }
                """.formatted(
                pkg,
                domainPkg, entityClass,
                domainPkg, repoClass,
                apiPkg, entityClass,
                apiPkg, entityClass,
                className, repoClass, className, repoClass,
                entityClass, entityClass, entityClass, createArgs,
                entityClass, entityClass,
                entityClass,
                entityClass
        );

        return new GeneratedFile(className + ".java", pkg, content, GeneratedFile.FileType.SERVICE);
    }

    static GeneratedFile controller(AnalyzedEntity entity, List<AnalyzedApiEndpoint> endpoints,
                                     String basePackage) {
        String pkg = basePackage + "." + lower(entity.name()) + ".api";
        String entityClass = capitalize(entity.name());
        String className = entityClass + "Controller";
        String servicePkg = basePackage + "." + lower(entity.name()) + ".application";
        String resourcePath = "/api/v1/" + toSnakeCase(entity.name()) + "s";

        String content = """
                package %s;

                import %s.%sService;
                import jakarta.validation.Valid;
                import org.springframework.http.HttpStatus;
                import org.springframework.web.bind.annotation.*;
                import java.util.List;
                import java.util.UUID;

                @RestController
                @RequestMapping("%s")
                public class %s {

                    private final %sService service;

                    public %s(%sService service) {
                        this.service = service;
                    }

                    @PostMapping
                    @ResponseStatus(HttpStatus.CREATED)
                    %sResponse create(@Valid @RequestBody Create%sRequest request) {
                        return %sResponse.from(service.create(request));
                    }

                    @GetMapping("/{id}")
                    %sResponse get(@PathVariable UUID id) {
                        return %sResponse.from(service.get(id));
                    }

                    @GetMapping
                    List<%sResponse> list() {
                        return service.list().stream().map(%sResponse::from).toList();
                    }

                    @DeleteMapping("/{id}")
                    @ResponseStatus(HttpStatus.NO_CONTENT)
                    void delete(@PathVariable UUID id) {
                        service.delete(id);
                    }
                }
                """.formatted(
                pkg, servicePkg, entityClass, resourcePath, className,
                entityClass, className, entityClass,
                entityClass, entityClass, entityClass,
                entityClass, entityClass,
                entityClass, entityClass
        );

        return new GeneratedFile(className + ".java", pkg, content, GeneratedFile.FileType.CONTROLLER);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static String entityField(EntityField field) {
        String columnAnnotation = field.required()
                ? "    @Column(nullable = false)"
                : "    @Column";
        if ("UUID".equals(field.inferredType())) {
            return "    @Column(name = \"%s\")%n    private %s %s;".formatted(
                    toSnakeCase(field.name()), javaType(field.inferredType()), field.name());
        }
        return "%s\n    private %s %s;".formatted(columnAnnotation, javaType(field.inferredType()), field.name());
    }

    private static String buildValidationAnnotations(EntityField field, List<AnalyzedValidationRule> rules) {
        StringBuilder sb = new StringBuilder();
        String lower = field.name().toLowerCase();

        // @NotBlank only valid for CharSequence (String) — use @NotNull for other types
        String type = javaType(field.inferredType());
        if (field.required()) sb.append("String".equals(type) ? "@NotBlank " : "@NotNull ");
        if (lower.contains("email")) sb.append("@Email ");
        if (lower.contains("url")) sb.append("@Pattern(regexp = \"^https?://.*\") ");

        for (AnalyzedValidationRule rule : rules) {
            if (!rule.targetField().toLowerCase().contains(lower)) continue;
            switch (rule.ruleType()) {
                case "MAX_LENGTH" -> {
                    String max = rule.description().replaceAll(".*?(\\d+).*", "$1");
                    sb.append("@Size(max = %s) ".formatted(max));
                }
                case "MIN_LENGTH" -> {
                    String min = rule.description().replaceAll(".*?(\\d+).*", "$1");
                    sb.append("@Size(min = %s) ".formatted(min));
                }
                case "POSITIVE" -> sb.append("@Positive ");
                case "NOT_NULL" -> sb.append("@NotNull ");
                case "PATTERN", "FORMAT" -> {
                    String desc = rule.description().toLowerCase();
                    String regex;
                    // Try to extract an explicit regex like ^...$ from the description
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\^[^$]+\\$)").matcher(rule.description());
                    if (m.find()) {
                        regex = m.group(1);
                    } else if (desc.contains("no space") || desc.contains("no special") || desc.contains("alphanumeric")) {
                        regex = "^[a-zA-Z0-9]+$";
                    } else if (desc.contains("email")) {
                        regex = "^[\\\\w._%+\\\\-]+@[\\\\w.\\\\-]+\\\\.[a-zA-Z]{2,}$";
                    } else {
                        regex = "^[a-zA-Z0-9]+$";
                    }
                    sb.append("@Pattern(regexp = \"%s\") ".formatted(regex));
                }
            }
        }
        return sb.toString().isBlank() ? "" : "\n            " + sb.toString().strip() + "\n            ";
    }

    static String javaType(String inferredType) {
        return switch (inferredType == null ? "String" : inferredType) {
            case "UUID" -> "UUID";
            case "Integer" -> "Integer";
            case "Long" -> "Long";
            case "BigDecimal" -> "java.math.BigDecimal";
            case "Boolean" -> "Boolean";
            case "Instant" -> "Instant";
            default -> "String";
        };
    }

    static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    static String lower(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    static String toSnakeCase(String s) {
        return s.replaceAll("([A-Z])", "_$1").toLowerCase().replaceAll("^_", "");
    }
}
