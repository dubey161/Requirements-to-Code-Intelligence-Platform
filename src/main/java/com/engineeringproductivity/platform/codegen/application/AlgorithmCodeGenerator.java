package com.engineeringproductivity.platform.codegen.application;

import com.engineeringproductivity.platform.codegen.domain.GeneratedFile;
import com.engineeringproductivity.platform.requirement.analysis.application.GenerationPlanner;
import com.engineeringproductivity.platform.requirement.analysis.application.gateway.LlmGateway;
import com.engineeringproductivity.platform.requirement.analysis.domain.model.AnalyzedFunctionalRequirement;
import com.engineeringproductivity.platform.requirement.analysis.domain.model.RequirementKnowledgeModel;
import com.engineeringproductivity.platform.requirement.domain.RequirementStory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Generates a standalone algorithm Java class — NO Spring Boot, NO JPA, NO REST.
 *
 * Strategy:
 * 1. If LLM is available: ask LLM to write the actual algorithm
 * 2. Fallback: generate a well-structured scaffold with the problem description
 *    in comments, correctly typed method signatures, and TODO body
 *
 * The generated class will ALWAYS have:
 *   - main() method with sample usage
 *   - solve() / solution() method with correct signature
 *   - Time + space complexity comment
 *   - NO @Entity, @Repository, @Service, @RestController
 */
class AlgorithmCodeGenerator {

    private static final Logger log = LoggerFactory.getLogger(AlgorithmCodeGenerator.class);

    private static final String SYSTEM_PROMPT = """
            You are a senior Java engineer solving algorithmic problems.

            Generate a single, complete, standalone Java class.

            STRICT RULES:
            - NO import of Spring, JPA, Hibernate, Lombok, or any external framework
            - Only use java.util.*, java.io.*, java.math.* if needed
            - Class MUST have: public static void main(String[] args)
            - Class MUST have: public static <returnType> solve(<params>) method
            - Include TIME COMPLEXITY and SPACE COMPLEXITY in a comment block above solve()
            - Include 2-3 sample test cases in main()
            - The solution must be CORRECT and handle all edge cases
            - Return ONLY raw Java code — no markdown, no code fences, no explanation
            """;

    static List<GeneratedFile> generate(RequirementKnowledgeModel model,
                                         RequirementStory story,
                                         Optional<LlmGateway> llmGateway,
                                         String basePackage) {
        String className = GenerationPlanner.deriveAlgorithmClassName(story.getTitle());

        String content = llmGateway
                .map(gw -> {
                    try {
                        return generateWithLlm(gw, story, model, className, basePackage);
                    } catch (Exception e) {
                        log.warn("LLM algorithm generation failed for {}, falling back to scaffold: {}",
                                className, e.getMessage());
                        return generateScaffold(story, model, className, basePackage);
                    }
                })
                .orElseGet(() -> generateScaffold(story, model, className, basePackage));

        return List.of(new GeneratedFile(
                className + ".java",
                basePackage,
                content,
                GeneratedFile.FileType.ALGORITHM
        ));
    }

    // ── LLM generation ────────────────────────────────────────────────────────

    private static String generateWithLlm(LlmGateway gateway, RequirementStory story,
                                           RequirementKnowledgeModel model,
                                           String className, String basePackage) {
        String frText = model.functionalRequirements().stream()
                .map(AnalyzedFunctionalRequirement::description)
                .collect(Collectors.joining("\n- ", "- ", ""));

        String userPrompt = """
                Generate a Java class named %s in package %s.

                === PROBLEM STATEMENT ===
                Title: %s

                Description:
                %s

                Requirements:
                %s

                Edge Cases:
                %s

                === OUTPUT FORMAT ===
                Return ONLY the complete Java source file content.
                Start with: package %s;
                Class name must be: %s
                """.formatted(
                className, basePackage,
                story.getTitle(),
                story.getDescription(),
                frText.isBlank() ? story.getDescription() : frText,
                model.edgeCases().isEmpty() ? "handle null/empty inputs gracefully"
                        : model.edgeCases().stream().map(e -> e.condition()).collect(Collectors.joining(", ")),
                basePackage, className
        );

        log.info("Requesting algorithm generation from LLM: class={}", className);
        String raw = gateway.completeText(SYSTEM_PROMPT, userPrompt);

        // Strip markdown fences if LLM wrapped code
        String code = raw.strip();
        if (code.startsWith("```")) {
            int firstNl = code.indexOf('\n');
            int lastFence = code.lastIndexOf("```");
            if (firstNl > 0 && lastFence > firstNl) {
                code = code.substring(firstNl + 1, lastFence).strip();
            }
        }

        // Ensure it starts with the package declaration
        if (!code.startsWith("package ")) {
            code = "package " + basePackage + ";\n\n" + code;
        }

        return code;
    }

    // ── Scaffold fallback ─────────────────────────────────────────────────────

    private static String generateScaffold(RequirementStory story,
                                            RequirementKnowledgeModel model,
                                            String className, String basePackage) {
        String frComments = model.functionalRequirements().stream()
                .map(fr -> " * " + fr.description())
                .collect(Collectors.joining("\n"));

        String edgeCaseComments = model.edgeCases().stream()
                .map(ec -> " *   - " + ec.condition() + " → " + ec.expectedBehavior())
                .collect(Collectors.joining("\n"));

        return """
                package %s;

                /**
                 * %s
                 *
                 * Problem:
                 * %s
                 *
                 * Requirements:
                %s
                 *
                 * Edge Cases:
                %s
                 */
                public class %s {

                    /**
                     * Time Complexity:  O(?)  — TODO: analyze
                     * Space Complexity: O(?)  — TODO: analyze
                     *
                     * @param  — TODO: add parameters based on problem input
                     * @return — TODO: return type based on problem output
                     */
                    public static Object solve(Object input) {
                        // TODO: implement solution
                        throw new UnsupportedOperationException("Not implemented yet");
                    }

                    public static void main(String[] args) {
                        // TODO: add sample test cases
                        // Example 1:
                        // Object result = solve(/* sample input */);
                        // System.out.println("Output: " + result);

                        System.out.println("Problem: %s");
                        System.out.println("Implement solve() above");
                    }
                }
                """.formatted(
                basePackage,
                story.getTitle(),
                story.getDescription().replace("\n", "\n * "),
                frComments.isBlank() ? " * (no functional requirements extracted)" : frComments,
                edgeCaseComments.isBlank() ? " *   - handle null/empty inputs" : edgeCaseComments,
                className,
                story.getTitle().replace("\"", "\\\"")
        );
    }
}
