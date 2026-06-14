package com.engineeringproductivity.platform.compliance.application;

import com.engineeringproductivity.platform.compliance.domain.ComplianceGap;
import com.engineeringproductivity.platform.compliance.domain.ComplianceGap.GapCategory;
import com.engineeringproductivity.platform.compliance.domain.ComplianceGap.Severity;
import com.engineeringproductivity.platform.github.GitHubClient.PrFile;
import com.engineeringproductivity.platform.requirement.analysis.domain.model.AnalyzedApiEndpoint;
import com.engineeringproductivity.platform.requirement.analysis.domain.model.AnalyzedEntity;
import com.engineeringproductivity.platform.requirement.analysis.domain.model.AnalyzedFunctionalRequirement;
import com.engineeringproductivity.platform.requirement.analysis.domain.model.AnalyzedSecurityRequirement;
import com.engineeringproductivity.platform.requirement.analysis.domain.model.AnalyzedValidationRule;
import com.engineeringproductivity.platform.requirement.analysis.domain.model.RequirementKnowledgeModel;
import com.engineeringproductivity.platform.requirement.analysis.domain.model.RequirementType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Compares a RequirementKnowledgeModel against what was actually implemented in a PR.
 * Uses JavaParser AST analysis for accurate checks instead of string matching.
 */
@Component
public class ComplianceChecker {

    private final JavaAstParser astParser;

    public ComplianceChecker(JavaAstParser astParser) {
        this.astParser = astParser;
    }

    public ComplianceResult check(RequirementKnowledgeModel model, List<PrFile> prFiles) {
        List<ComplianceGap> gaps = new ArrayList<>();

        // Parse actual AST from diff — much more accurate than string matching
        JavaAstParser.ParsedPrContent ast = astParser.parse(prFiles);

        // Route compliance checks based on requirement type
        RequirementType type = model.resolvedType();
        if (type == RequirementType.ALGORITHM) {
            checkAlgorithmCompliance(model, ast, prFiles, gaps);
        } else {
            checkEntities(model.entities(), ast, gaps);
            checkEndpoints(model.apiEndpoints(), ast, gaps);
            checkValidations(model.validationRules(), ast, gaps);
            checkSecurity(model.securityRequirements(), ast, gaps);
            checkFunctionalRequirements(model.functionalRequirements(), prFiles, gaps);
        }

        int score = calculateScore(model, gaps);
        return new ComplianceResult(gaps, score);
    }

    // ── Algorithm compliance ──────────────────────────────────────────────────

    private void checkAlgorithmCompliance(RequirementKnowledgeModel model,
                                           JavaAstParser.ParsedPrContent ast,
                                           List<PrFile> prFiles, List<ComplianceGap> gaps) {
        // 1. Check that a Solution/algorithm class is present (not a CRUD entity)
        boolean hasAlgorithmClass = !ast.entityClasses().isEmpty()
                || prFiles.stream().anyMatch(f -> f.filename() != null && f.filename().endsWith(".java"));
        if (!hasAlgorithmClass) {
            gaps.add(new ComplianceGap(GapCategory.MISSING_FUNCTIONAL_REQUIREMENT, Severity.CRITICAL,
                    "No Java class found in PR for algorithm problem",
                    "A standalone Java class with solve() method",
                    "PR diff contained no Java files"));
        }

        // 2. Check that main() method exists (detectable from diff text)
        boolean hasMain = prFiles.stream().anyMatch(f ->
                f.patch() != null && f.patch().lines()
                        .anyMatch(l -> l.contains("public static void main")));
        if (!hasMain) {
            gaps.add(new ComplianceGap(GapCategory.MISSING_FUNCTIONAL_REQUIREMENT, Severity.HIGH,
                    "main() method not found in generated algorithm class",
                    "public static void main(String[] args)",
                    "Required for standalone execution and testing"));
        }

        // 3. Check that CRUD artifacts are NOT present — they'd mean wrong generator was used
        boolean hasCrudArtifact = !ast.repositoryInterfaces().isEmpty() || !ast.controllerClasses().isEmpty();
        if (hasCrudArtifact) {
            gaps.add(new ComplianceGap(GapCategory.MISSING_FUNCTIONAL_REQUIREMENT, Severity.CRITICAL,
                    "CRUD artifacts (@Entity/@Repository/@Controller) found for an ALGORITHM requirement",
                    "Standalone algorithm class only — no Spring Boot artifacts",
                    "Found: repositories=" + ast.repositoryInterfaces() + ", controllers=" + ast.controllerClasses() +
                    " — wrong generator was used. This is a code quality FAIL."));
        }

        // 4. Check functional requirements coverage
        checkFunctionalRequirements(model.functionalRequirements(), prFiles, gaps);
    }

    // ── Entity compliance (AST-based) ─────────────────────────────────────────

    private void checkEntities(List<AnalyzedEntity> entities,
                                JavaAstParser.ParsedPrContent ast, List<ComplianceGap> gaps) {
        for (AnalyzedEntity entity : entities) {
            String name = entity.name();

            boolean entityFound = ast.entityClasses().stream()
                    .anyMatch(c -> c.equalsIgnoreCase(name));
            boolean repoFound = ast.repositoryInterfaces().stream()
                    .anyMatch(c -> c.toLowerCase().contains(name.toLowerCase()));
            boolean controllerFound = ast.controllerClasses().stream()
                    .anyMatch(c -> c.toLowerCase().contains(name.toLowerCase()));

            if (!entityFound) {
                gaps.add(new ComplianceGap(GapCategory.MISSING_ENTITY, Severity.CRITICAL,
                        "@Entity class not found in PR diff",
                        name + " (annotated with @Entity)",
                        "AST found entities: " + ast.entityClasses()));
            }
            if (!repoFound) {
                gaps.add(new ComplianceGap(GapCategory.MISSING_ENTITY, Severity.HIGH,
                        "Repository interface not found for " + name,
                        name + "Repository extends JpaRepository",
                        "AST found repositories: " + ast.repositoryInterfaces()));
            }
            if (!controllerFound) {
                gaps.add(new ComplianceGap(GapCategory.MISSING_ENDPOINT, Severity.HIGH,
                        "@RestController not found for " + name,
                        name + "Controller",
                        "AST found controllers: " + ast.controllerClasses()));
            }
        }
    }

    // ── Endpoint compliance (AST path extraction) ─────────────────────────────

    private void checkEndpoints(List<AnalyzedApiEndpoint> endpoints,
                                 JavaAstParser.ParsedPrContent ast, List<ComplianceGap> gaps) {
        if (ast.requestMappingPaths().isEmpty() && !endpoints.isEmpty()) {
            gaps.add(new ComplianceGap(GapCategory.MISSING_ENDPOINT, Severity.HIGH,
                    "No @RequestMapping paths found in PR diff",
                    "At least one REST endpoint mapping",
                    "AST found 0 mapping annotations"));
            return;
        }

        for (AnalyzedApiEndpoint endpoint : endpoints) {
            String fragment = extractPathFragment(endpoint.suggestedPath());
            boolean pathFound = ast.requestMappingPaths().stream()
                    .anyMatch(p -> p.contains(fragment));
            if (!pathFound) {
                gaps.add(new ComplianceGap(GapCategory.MISSING_ENDPOINT, Severity.MEDIUM,
                        "Expected endpoint path not found in PR mappings",
                        endpoint.httpMethod() + " " + endpoint.suggestedPath(),
                        "AST found paths: " + ast.requestMappingPaths()));
            }
        }
    }

    // ── Validation compliance (AST annotation check) ──────────────────────────

    private void checkValidations(List<AnalyzedValidationRule> rules,
                                   JavaAstParser.ParsedPrContent ast, List<ComplianceGap> gaps) {
        if (rules.isEmpty()) return;

        if (ast.validationAnnotations().isEmpty()) {
            gaps.add(new ComplianceGap(GapCategory.MISSING_VALIDATION, Severity.HIGH,
                    "%d validation rules required but no validation annotations found in PR".formatted(rules.size()),
                    "@NotBlank, @NotNull, @Size, @Email, @Valid, etc.",
                    "AST found 0 validation annotations"));
        }
    }

    // ── Security compliance (AST annotation check) ────────────────────────────

    private void checkSecurity(List<AnalyzedSecurityRequirement> requirements,
                                JavaAstParser.ParsedPrContent ast, List<ComplianceGap> gaps) {
        for (AnalyzedSecurityRequirement req : requirements) {
            boolean found = switch (req.category()) {
                case "AUTHENTICATION", "AUTHORIZATION" ->
                        !ast.securityAnnotations().isEmpty();
                case "DATA_PROTECTION" -> {
                    // Check for hashing/encryption in diff text
                    yield ast.entityClasses().stream().anyMatch(c -> c.contains("Password"))
                            || !ast.securityAnnotations().isEmpty();
                }
                default -> true;
            };

            if (!found) {
                gaps.add(new ComplianceGap(GapCategory.MISSING_SECURITY, Severity.CRITICAL,
                        "Security requirement not addressed in PR: " + req.category(),
                        req.description(),
                        "AST found security annotations: " + ast.securityAnnotations()));
            }
        }
    }

    // ── Functional requirement compliance ─────────────────────────────────────

    private void checkFunctionalRequirements(List<AnalyzedFunctionalRequirement> requirements,
                                              List<PrFile> prFiles, List<ComplianceGap> gaps) {
        long mustCount = requirements.stream().filter(r -> "MUST".equals(r.priority())).count();
        if (mustCount == 0) return;

        long additions = prFiles.stream()
                .mapToLong(f -> f.patch() == null ? 0 :
                        f.patch().lines().filter(l -> l.startsWith("+") && !l.startsWith("+++")).count())
                .sum();

        if (additions < mustCount * 5) {
            gaps.add(new ComplianceGap(GapCategory.MISSING_FUNCTIONAL_REQUIREMENT, Severity.MEDIUM,
                    "PR may be too small for %d MUST requirements".formatted(mustCount),
                    "at least %d additions for %d requirements".formatted(mustCount * 5, mustCount),
                    "%d additions found".formatted(additions)));
        }
    }

    // ── Score calculation ─────────────────────────────────────────────────────

    private int calculateScore(RequirementKnowledgeModel model, List<ComplianceGap> gaps) {
        if (gaps.isEmpty()) return 100;

        int penalty = 0;
        for (ComplianceGap gap : gaps) {
            penalty += switch (gap.severity()) {
                case CRITICAL -> 20;
                case HIGH -> 12;
                case MEDIUM -> 6;
                case LOW -> 2;
            };
        }

        return Math.max(0, 100 - penalty);
    }

    private String extractPathFragment(String suggestedPath) {
        // Extract the resource name from /api/v1/resources/{id} → "resources"
        String[] parts = suggestedPath.split("/");
        for (int i = parts.length - 1; i >= 0; i--) {
            if (!parts[i].isEmpty() && !parts[i].startsWith("{")) {
                return parts[i];
            }
        }
        return suggestedPath;
    }

    public record ComplianceResult(List<ComplianceGap> gaps, int score) {}
}
