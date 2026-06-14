package com.engineeringproductivity.platform.requirement.analysis.application;

import com.engineeringproductivity.platform.requirement.analysis.domain.model.GenerationPlan;
import com.engineeringproductivity.platform.requirement.analysis.domain.model.RequirementKnowledgeModel;
import com.engineeringproductivity.platform.requirement.analysis.domain.model.RequirementType;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Builds a GenerationPlan from a RequirementType and the extracted knowledge model.
 * The plan is deterministic — no LLM needed. It tells every downstream generator:
 *   - exactly which files to create (expectedArtifacts)
 *   - what constructs must be present (mustHave)
 *   - what constructs are forbidden (mustNotHave)
 *
 * This prevents the generator from producing nonsense (e.g. @Entity for an algorithm).
 */
@Component
public class GenerationPlanner {

    public GenerationPlan plan(RequirementType type, RequirementKnowledgeModel model, String title) {
        return switch (type) {
            case ALGORITHM       -> planAlgorithm(model, title);
            case SPRING_CRUD     -> planCrud(model);
            case REST_API        -> planRestApi(model);
            case MICROSERVICE    -> planMicroservice(model);
            case BATCH_JOB       -> planBatch(model, title);
            case CLI_APPLICATION -> planCli(title);
            case LIBRARY         -> planLibrary(title);
        };
    }

    // ── Algorithm ─────────────────────────────────────────────────────────────

    private GenerationPlan planAlgorithm(RequirementKnowledgeModel model, String title) {
        String className = deriveAlgorithmClassName(title);
        return new GenerationPlan(
                List.of(className + ".java"),
                List.of("main method", "solve/solution method", "time complexity comment", "sample input/output"),
                List.of("@Entity", "@Repository", "@RestController", "@Service",
                        "JpaRepository", "DataSource", "spring.datasource", "@SpringBootApplication"),
                "Standalone algorithm class: " + className + " — no Spring, no DB"
        );
    }

    // ── Spring CRUD ───────────────────────────────────────────────────────────

    private GenerationPlan planCrud(RequirementKnowledgeModel model) {
        List<String> artifacts = model.entities().stream()
                .flatMap(e -> List.of(
                        e.name() + ".java",
                        e.name() + "Repository.java",
                        e.name() + "Service.java",
                        e.name() + "Controller.java",
                        "Create" + e.name() + "Request.java",
                        e.name() + "Response.java"
                ).stream())
                .toList();

        return new GenerationPlan(
                artifacts,
                List.of("@Entity", "JpaRepository", "@RestController", "@Service", "@Valid"),
                List.of("public static void main"),
                "Standard Spring Boot CRUD for: " + model.entities().stream().map(e -> e.name()).toList()
        );
    }

    // ── REST API (non-CRUD) ───────────────────────────────────────────────────

    private GenerationPlan planRestApi(RequirementKnowledgeModel model) {
        return new GenerationPlan(
                List.of("ApiController.java", "ApiService.java", "ApiRequest.java", "ApiResponse.java"),
                List.of("@RestController", "@Service", "@RequestMapping"),
                List.of("public static void main"),
                "REST API endpoints: " + model.apiEndpoints().size() + " endpoints"
        );
    }

    // ── Microservice / Event-driven ───────────────────────────────────────────

    private GenerationPlan planMicroservice(RequirementKnowledgeModel model) {
        return new GenerationPlan(
                List.of("EventConsumer.java", "EventProducer.java", "EventConfig.java"),
                List.of("@KafkaListener", "@EnableKafka", "KafkaTemplate"),
                List.of("@Entity", "JpaRepository"),
                "Event-driven microservice with Kafka"
        );
    }

    // ── Batch job ─────────────────────────────────────────────────────────────

    private GenerationPlan planBatch(RequirementKnowledgeModel model, String title) {
        String name = toPascalCase(title);
        return new GenerationPlan(
                List.of(name + "BatchJob.java", name + "Processor.java"),
                List.of("@Scheduled", "ItemProcessor", "ItemReader", "ItemWriter"),
                List.of("@RestController"),
                "Batch job: " + title
        );
    }

    // ── CLI ───────────────────────────────────────────────────────────────────

    private GenerationPlan planCli(String title) {
        String name = toPascalCase(title);
        return new GenerationPlan(
                List.of(name + "Cli.java"),
                List.of("main method", "args[] parsing"),
                List.of("@Entity", "@RestController", "JpaRepository"),
                "CLI application: " + title
        );
    }

    // ── Library ───────────────────────────────────────────────────────────────

    private GenerationPlan planLibrary(String title) {
        String name = toPascalCase(title);
        return new GenerationPlan(
                List.of(name + ".java"),
                List.of("public static methods"),
                List.of("@Entity", "@RestController", "JpaRepository", "public static void main"),
                "Utility/library class: " + title
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    public static String deriveAlgorithmClassName(String title) {
        // "Weighted Word Mapping" → "WeightedWordMapping"
        // "Two Sum" → "TwoSum"
        String[] words = title.trim().split("[\\s\\-_]+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0)));
                if (w.length() > 1) sb.append(w.substring(1).toLowerCase());
            }
        }
        String name = sb.toString().replaceAll("[^a-zA-Z0-9]", "");
        return name.isEmpty() ? "Solution" : name;
    }

    private String toPascalCase(String title) {
        return deriveAlgorithmClassName(title);
    }
}
