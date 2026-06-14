package com.engineeringproductivity.platform.search;

import com.engineeringproductivity.platform.codegen.domain.GeneratedCodeBundle;
import com.engineeringproductivity.platform.codegen.domain.GeneratedFile;
import com.engineeringproductivity.platform.requirement.analysis.domain.RequirementAnalysis;
import com.engineeringproductivity.platform.requirement.analysis.domain.model.RequirementKnowledgeModel;
import com.engineeringproductivity.platform.requirement.domain.RequirementStory;
import com.engineeringproductivity.platform.search.document.GeneratedCodeSearchDocument;
import com.engineeringproductivity.platform.search.document.GeneratedCodeSearchRepository;
import com.engineeringproductivity.platform.search.document.RequirementSearchDocument;
import com.engineeringproductivity.platform.search.document.RequirementSearchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Indexes pipeline artefacts into Elasticsearch after each stage.
 * All methods are @Async — indexing failures never fail the primary pipeline.
 */
@Service
@ConditionalOnProperty(name = "spring.data.elasticsearch.repositories.enabled", havingValue = "true")
public class ElasticsearchIndexingService {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchIndexingService.class);

    private final RequirementSearchRepository requirementRepo;
    private final GeneratedCodeSearchRepository codeRepo;
    private final EmbeddingService embeddingService;

    public ElasticsearchIndexingService(RequirementSearchRepository requirementRepo,
                                         GeneratedCodeSearchRepository codeRepo,
                                         EmbeddingService embeddingService) {
        this.requirementRepo = requirementRepo;
        this.codeRepo = codeRepo;
        this.embeddingService = embeddingService;
    }

    // ── Stage 1: Analysis complete ────────────────────────────────────────────

    @Async("analysisExecutor")
    public void indexRequirement(RequirementStory story, RequirementAnalysis analysis) {
        try {
            RequirementKnowledgeModel model = analysis.getKnowledgeModel();

            String textToEmbed = story.getTitle() + "\n" + story.getDescription();
            float[] embedding = embeddingService.embed(textToEmbed);

            RequirementSearchDocument doc = requirementRepo
                    .findById(story.getId().toString())
                    .orElse(new RequirementSearchDocument());

            doc.setId(story.getId().toString());
            doc.setExternalKey(story.getExternalKey());
            doc.setTitle(story.getTitle());
            doc.setDescription(story.getDescription());
            doc.setAcceptanceCriteria(story.getAcceptanceCriteria());
            doc.setStatus(story.getStatus().name());
            doc.setEntityNames(model.entities().stream()
                    .map(e -> e.name())
                    .collect(Collectors.toList()));
            doc.setEndpointPaths(model.apiEndpoints().stream()
                    .map(e -> e.suggestedPath())
                    .collect(Collectors.toList()));
            doc.setValidationFields(model.validationRules().stream()
                    .map(r -> r.targetField())
                    .collect(Collectors.toList()));
            doc.setSecurityCategories(model.securityRequirements().stream()
                    .map(s -> s.category())
                    .collect(Collectors.toList()));
            if (embedding.length > 0) {
                doc.setEmbedding(embedding);
            }
            doc.setIndexedAt(Instant.now());

            requirementRepo.save(doc);
            log.info("Indexed requirement {} to Elasticsearch", story.getExternalKey());
        } catch (Exception e) {
            log.warn("Failed to index requirement {}: {}", story.getId(), e.getMessage());
        }
    }

    // ── Stage 2: Code generation complete ────────────────────────────────────

    @Async("pipelineExecutor")
    public void indexGeneratedCode(UUID requirementId, String externalKey, GeneratedCodeBundle bundle) {
        try {
            for (GeneratedFile file : bundle.getGeneratedFiles()) {
                String docId = requirementId + "-" + file.fileName();
                float[] embedding = embeddingService.embed(file.content());

                GeneratedCodeSearchDocument doc = codeRepo.findById(docId)
                        .orElse(new GeneratedCodeSearchDocument());
                doc.setId(docId);
                doc.setRequirementId(requirementId.toString());
                doc.setExternalKey(externalKey);
                doc.setFileName(file.fileName());
                doc.setFileType(file.fileType().name());
                doc.setContent(file.content());
                if (embedding.length > 0) {
                    doc.setEmbedding(embedding);
                }
                doc.setIndexedAt(Instant.now());
                codeRepo.save(doc);
            }
            log.info("Indexed {} generated files for {} to Elasticsearch",
                    bundle.getGeneratedFiles().size(), externalKey);
        } catch (Exception e) {
            log.warn("Failed to index generated code for {}: {}", requirementId, e.getMessage());
        }
    }

    // ── Stage 3: Compliance check complete ───────────────────────────────────

    @Async("pipelineExecutor")
    public void updateComplianceScore(UUID requirementId, int complianceScore) {
        try {
            requirementRepo.findById(requirementId.toString()).ifPresent(doc -> {
                doc.setComplianceScore(complianceScore);
                doc.setIndexedAt(Instant.now());
                requirementRepo.save(doc);
                log.info("Updated compliance score={} for {} in Elasticsearch",
                        complianceScore, requirementId);
            });
        } catch (Exception e) {
            log.warn("Failed to update compliance score for {}: {}", requirementId, e.getMessage());
        }
    }

    // ── Stage 4: Risk scoring complete ───────────────────────────────────────

    @Async("pipelineExecutor")
    public void updateRiskScore(UUID requirementId, int riskScore, String riskLevel) {
        try {
            requirementRepo.findById(requirementId.toString()).ifPresent(doc -> {
                doc.setRiskScore(riskScore);
                doc.setRiskLevel(riskLevel);
                doc.setIndexedAt(Instant.now());
                requirementRepo.save(doc);
                log.info("Updated risk score={} level={} for {} in Elasticsearch",
                        riskScore, riskLevel, requirementId);
            });
        } catch (Exception e) {
            log.warn("Failed to update risk score for {}: {}", requirementId, e.getMessage());
        }
    }
}
