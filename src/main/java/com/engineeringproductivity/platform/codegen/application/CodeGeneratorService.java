package com.engineeringproductivity.platform.codegen.application;

import com.engineeringproductivity.platform.codegen.domain.GeneratedCodeBundle;
import com.engineeringproductivity.platform.codegen.domain.GeneratedCodeBundleRepository;
import com.engineeringproductivity.platform.codegen.domain.GeneratedFile;
import com.engineeringproductivity.platform.common.api.ResourceNotFoundException;
import com.engineeringproductivity.platform.requirement.analysis.application.RequirementAnalyzerService;
import com.engineeringproductivity.platform.requirement.analysis.application.gateway.LlmGateway;
import com.engineeringproductivity.platform.requirement.analysis.domain.RequirementAnalysis;
import com.engineeringproductivity.platform.requirement.analysis.domain.model.AnalyzedEntity;
import com.engineeringproductivity.platform.requirement.analysis.domain.model.RequirementKnowledgeModel;
import com.engineeringproductivity.platform.requirement.analysis.domain.model.RequirementType;
import com.engineeringproductivity.platform.requirement.domain.RequirementStory;
import com.engineeringproductivity.platform.requirement.domain.RequirementStoryRepository;
import com.engineeringproductivity.platform.search.ElasticsearchIndexingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class CodeGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(CodeGeneratorService.class);

    private final RequirementAnalyzerService analyzerService;
    private final GeneratedCodeBundleRepository bundleRepository;
    private final RequirementStoryRepository storyRepository;
    private final Optional<ElasticsearchIndexingService> indexingService;
    private final Optional<LlmGateway> llmGateway;

    @Value("${codegen.base-package:com.example.generated}")
    private String basePackage;

    public CodeGeneratorService(RequirementAnalyzerService analyzerService,
                                 GeneratedCodeBundleRepository bundleRepository,
                                 RequirementStoryRepository storyRepository,
                                 Optional<ElasticsearchIndexingService> indexingService,
                                 Optional<LlmGateway> llmGateway) {
        this.analyzerService = analyzerService;
        this.bundleRepository = bundleRepository;
        this.storyRepository = storyRepository;
        this.indexingService = indexingService;
        this.llmGateway = llmGateway;
    }

    @Transactional
    public GeneratedCodeBundle generate(UUID requirementId) {
        RequirementAnalysis analysis = analyzerService.getAnalysis(requirementId);
        RequirementKnowledgeModel model = analysis.getKnowledgeModel();
        RequirementType type = model.resolvedType();

        RequirementStory story = storyRepository.findById(requirementId)
                .orElseThrow(() -> new ResourceNotFoundException("Requirement not found: " + requirementId));

        log.info("Generating code for requirement: {} (type={})", requirementId, type);

        List<GeneratedFile> files = generateByType(type, model, story);

        log.info("Generated {} files for requirement: {} (type={})", files.size(), requirementId, type);

        GeneratedCodeBundle bundle = bundleRepository.findByRequirementId(requirementId)
                .map(existing -> {
                    existing.refresh(files);
                    return bundleRepository.save(existing);
                })
                .orElseGet(() -> bundleRepository.save(
                        GeneratedCodeBundle.create(requirementId, files, basePackage)));

        indexingService.ifPresent(s -> s.indexGeneratedCode(requirementId, story.getExternalKey(), bundle));

        return bundle;
    }

    // ── Generation routing ────────────────────────────────────────────────────

    private List<GeneratedFile> generateByType(RequirementType type,
                                                RequirementKnowledgeModel model,
                                                RequirementStory story) {
        return switch (type) {
            case ALGORITHM -> {
                log.info("Routing to AlgorithmCodeGenerator for: {}", story.getTitle());
                yield AlgorithmCodeGenerator.generate(model, story, llmGateway, basePackage);
            }
            case SPRING_CRUD, REST_API -> {
                log.info("Routing to CRUD generator for {} entities", model.entities().size());
                yield generateCrud(model);
            }
            default -> {
                // For MICROSERVICE, BATCH_JOB, CLI, LIBRARY — generate what we can from entities
                // TODO: dedicated generators for each type in future phases
                log.info("No dedicated generator for type={}, falling back to CRUD", type);
                yield generateCrud(model);
            }
        };
    }

    private List<GeneratedFile> generateCrud(RequirementKnowledgeModel model) {
        List<GeneratedFile> files = new ArrayList<>();
        for (AnalyzedEntity entity : model.entities()) {
            log.info("Generating CRUD files for entity: {}", entity.name());
            files.add(JavaCodeTemplates.entity(entity, basePackage));
            files.add(JavaCodeTemplates.repository(entity, basePackage));
            files.add(JavaCodeTemplates.createRequest(entity, model.validationRules(), basePackage));
            files.add(JavaCodeTemplates.response(entity, basePackage));
            files.add(JavaCodeTemplates.service(entity, basePackage));
            files.add(JavaCodeTemplates.controller(entity, model.apiEndpoints(), basePackage));
        }
        return files;
    }

    @Transactional(readOnly = true)
    public GeneratedCodeBundle getBundle(UUID requirementId) {
        return bundleRepository.findByRequirementId(requirementId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No generated code found for requirement: " + requirementId +
                        ". Trigger generation via POST /api/v1/requirements/{id}/code"));
    }
}
