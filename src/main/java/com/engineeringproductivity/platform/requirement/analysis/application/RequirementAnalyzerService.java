package com.engineeringproductivity.platform.requirement.analysis.application;

import com.engineeringproductivity.platform.common.api.ResourceNotFoundException;
import com.engineeringproductivity.platform.common.async.RequirementCreatedEvent;
import com.engineeringproductivity.platform.requirement.analysis.domain.RequirementAnalysis;
import com.engineeringproductivity.platform.requirement.analysis.domain.RequirementAnalysisRepository;
import com.engineeringproductivity.platform.requirement.analysis.domain.model.GenerationPlan;
import com.engineeringproductivity.platform.requirement.analysis.domain.model.RequirementKnowledgeModel;
import com.engineeringproductivity.platform.requirement.analysis.domain.model.RequirementType;
import com.engineeringproductivity.platform.requirement.domain.RequirementStory;
import com.engineeringproductivity.platform.requirement.domain.RequirementStoryRepository;
import com.engineeringproductivity.platform.search.ElasticsearchIndexingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class RequirementAnalyzerService {

    private static final Logger log = LoggerFactory.getLogger(RequirementAnalyzerService.class);

    private final RequirementStoryRepository requirementStoryRepository;
    private final RequirementAnalysisRepository analysisRepository;
    private final RequirementAnalyzer analyzer;
    private final RequirementClassifier classifier;
    private final GenerationPlanner planner;
    private final Optional<ElasticsearchIndexingService> indexingService;

    public RequirementAnalyzerService(
            RequirementStoryRepository requirementStoryRepository,
            RequirementAnalysisRepository analysisRepository,
            RequirementAnalyzer analyzer,
            RequirementClassifier classifier,
            GenerationPlanner planner,
            Optional<ElasticsearchIndexingService> indexingService
    ) {
        this.requirementStoryRepository = requirementStoryRepository;
        this.analysisRepository = analysisRepository;
        this.analyzer = analyzer;
        this.classifier = classifier;
        this.planner = planner;
        this.indexingService = indexingService;
    }

    /**
     * Listens for RequirementCreatedEvent after the creating transaction commits
     * and runs analysis in the background using the analysisExecutor thread pool.
     */
    @Async("analysisExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRequirementCreated(RequirementCreatedEvent event) {
        log.info("Background analysis triggered for: {}", event.externalKey());
        try {
            analyze(event.requirementId());
        } catch (Exception e) {
            log.error("Background analysis failed for: {}", event.externalKey(), e);
        }
    }

    /**
     * Triggers analysis on a requirement. If analysis already exists, re-analyzes.
     * Updates the RequirementStory status to ANALYZED or ANALYSIS_FAILED.
     */
    @Transactional
    public RequirementAnalysis analyze(UUID requirementId) {
        RequirementStory story = requirementStoryRepository.findById(requirementId)
                .orElseThrow(() -> new ResourceNotFoundException("Requirement not found: " + requirementId));

        // Allow re-analysis from ANALYZED or ANALYSIS_FAILED states
        if (story.getStatus().isTerminal()) {
            story.resetForReanalysis();
        }

        try {
            log.info("Starting analysis for requirement: {}", story.getExternalKey());

            // ── Step 0: Classify BEFORE analysis so the analyzer gets type context ──
            RequirementType type = classifier.classify(
                    story.getTitle(), story.getDescription(), story.getAcceptanceCriteria());
            log.info("Requirement type classified as: {} for {}", type, story.getExternalKey());

            // Pass type to LLM analyzer so the prompt is tailored per type.
            // Works for both LlmRequirementAnalyzer directly and when wrapped in FallbackRequirementAnalyzer.
            RequirementKnowledgeModel rawModel = analyzeWithType(story, type);

            // ── Step 1: Enforce type constraints on raw model ─────────────────────
            // The rule-based fallback and even the LLM can hallucinate CRUD artifacts
            // for non-CRUD types (e.g. extracting every capitalized word in an algorithm
            // description as an entity). Sanitize based on type here as the definitive gate.
            RequirementKnowledgeModel sanitizedModel = sanitizeForType(rawModel, type);

            // ── Step 2: Build generation plan from type + extracted model ──────────
            GenerationPlan generationPlan = planner.plan(type, sanitizedModel, story.getTitle());
            log.info("Generation plan: {} artifacts, mustNotHave={}",
                    generationPlan.expectedArtifacts().size(), generationPlan.mustNotHave());

            // ── Step 3: Stamp type + plan onto the model ─────────────────────────
            RequirementKnowledgeModel model = new RequirementKnowledgeModel(
                    sanitizedModel.entities(),
                    sanitizedModel.apiEndpoints(),
                    sanitizedModel.validationRules(),
                    sanitizedModel.functionalRequirements(),
                    sanitizedModel.securityRequirements(),
                    sanitizedModel.edgeCases(),
                    type.name(),
                    generationPlan
            );

            RequirementAnalysis analysis = analysisRepository.findByRequirementId(requirementId)
                    .map(existing -> {
                        existing.refresh(model, analyzer.version());
                        return existing;
                    })
                    .orElseGet(() -> RequirementAnalysis.create(
                            requirementId, model, analyzer.version()));

            RequirementAnalysis saved = analysisRepository.save(analysis);
            story.markAnalyzed();
            requirementStoryRepository.save(story);

            indexingService.ifPresent(s -> s.indexRequirement(story, saved));

            log.info("Analysis complete for requirement: {} — type={}, entities={}, endpoints={}, validations={}, " +
                            "functional={}, security={}, edgeCases={}, plan={}",
                    story.getExternalKey(),
                    type,
                    model.entities().size(),
                    model.apiEndpoints().size(),
                    model.validationRules().size(),
                    model.functionalRequirements().size(),
                    model.securityRequirements().size(),
                    model.edgeCases().size(),
                    generationPlan.description()
            );

            return saved;

        } catch (Exception e) {
            log.error("Analysis failed for requirement: {}", story.getExternalKey(), e);
            story.markAnalysisFailed();
            requirementStoryRepository.save(story);
            throw new AnalysisFailedException("Analysis failed for requirement: " + requirementId, e);
        }
    }

    /**
     * Runs analysis with type-aware prompting.
     * If the analyzer is (or wraps) an LlmRequirementAnalyzer, passes the classified type
     * so the LLM prompt is tailored (e.g. ALGORITHM suppresses entity/endpoint extraction).
     */
    private RequirementKnowledgeModel analyzeWithType(RequirementStory story, RequirementType type) {
        String title       = story.getTitle();
        String description = story.getDescription();
        var ac             = story.getAcceptanceCriteria();

        // Direct LLM analyzer — call type-aware overload
        if (analyzer instanceof LlmRequirementAnalyzer llm) {
            return llm.analyze(title, description, ac, type.name());
        }
        // FallbackRequirementAnalyzer wrapping LLM — access primary via reflection-free cast
        if (analyzer instanceof FallbackRequirementAnalyzer fallback) {
            return fallback.analyzeWithType(title, description, ac, type.name());
        }
        // Rule-based or unknown — standard call
        return analyzer.analyze(title, description, ac);
    }

    /**
     * Removes artifacts that are forbidden for the given requirement type.
     *
     * The rule-based analyzer has no concept of requirement type and will always
     * extract entities/endpoints from any text (it sees capitalized nouns like
     * "Write", "Java", "Return", "Example" as entity candidates for algorithm problems).
     * The LLM can also hallucinate CRUD structures despite type-specific prompting.
     *
     * This method is the authoritative enforcement point: after all analysis,
     * before persisting, illegal fields are cleared.
     */
    private RequirementKnowledgeModel sanitizeForType(RequirementKnowledgeModel model, RequirementType type) {
        return switch (type) {
            case ALGORITHM, CLI_APPLICATION, LIBRARY ->
                // These types produce standalone classes — no DB entities, no REST endpoints, no auth
                new RequirementKnowledgeModel(
                        List.of(),
                        List.of(),
                        model.validationRules(),
                        model.functionalRequirements(),
                        List.of(),
                        model.edgeCases(),
                        null, null
                );
            case MICROSERVICE ->
                // Event-driven: no REST endpoints (or only EVENT-type ones), no JPA entities
                new RequirementKnowledgeModel(
                        List.of(),
                        model.apiEndpoints().stream()
                                .filter(e -> "EVENT".equals(e.httpMethod()))
                                .toList(),
                        model.validationRules(),
                        model.functionalRequirements(),
                        model.securityRequirements(),
                        model.edgeCases(),
                        null, null
                );
            default -> model; // SPRING_CRUD, REST_API, BATCH_JOB — keep everything
        };
    }

    /**
     * Retrieves the existing analysis for a requirement.
     */
    @Transactional(readOnly = true)
    public RequirementAnalysis getAnalysis(UUID requirementId) {
        if (!requirementStoryRepository.existsById(requirementId)) {
            throw new ResourceNotFoundException("Requirement not found: " + requirementId);
        }
        return analysisRepository.findByRequirementId(requirementId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No analysis found for requirement: " + requirementId +
                        ". Trigger analysis via POST /api/v1/requirements/{id}/analysis"));
    }
}
