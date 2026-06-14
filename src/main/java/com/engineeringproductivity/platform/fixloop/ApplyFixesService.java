package com.engineeringproductivity.platform.fixloop;

import com.engineeringproductivity.platform.aireview.application.AiPrReviewService;
import com.engineeringproductivity.platform.aireview.domain.AiPrReview;
import com.engineeringproductivity.platform.aireview.domain.AiReviewIssue;
import com.engineeringproductivity.platform.buildvalidation.application.BuildValidationService;
import com.engineeringproductivity.platform.buildvalidation.domain.BuildValidationReport;
import com.engineeringproductivity.platform.codegen.application.CodeGeneratorService;
import com.engineeringproductivity.platform.codegen.domain.GeneratedCodeBundle;
import com.engineeringproductivity.platform.compliance.application.ComplianceService;
import com.engineeringproductivity.platform.compliance.domain.ComplianceGap;
import com.engineeringproductivity.platform.compliance.domain.ComplianceReport;
import com.engineeringproductivity.platform.common.api.ResourceNotFoundException;
import com.engineeringproductivity.platform.github.GitHubClient;
import com.engineeringproductivity.platform.github.domain.CreatedPullRequest;
import com.engineeringproductivity.platform.github.domain.PullRequestRepository;
import com.engineeringproductivity.platform.requirement.analysis.application.RequirementAnalyzerService;
import com.engineeringproductivity.platform.requirement.analysis.application.gateway.LlmGateway;
import com.engineeringproductivity.platform.requirement.analysis.domain.RequirementAnalysis;
import com.engineeringproductivity.platform.requirement.analysis.domain.RequirementAnalysisRepository;
import com.engineeringproductivity.platform.requirement.analysis.domain.model.GenerationPlan;
import com.engineeringproductivity.platform.requirement.analysis.domain.model.RequirementKnowledgeModel;
import com.engineeringproductivity.platform.requirement.domain.RequirementStory;
import com.engineeringproductivity.platform.requirement.domain.RequirementStoryRepository;
import com.engineeringproductivity.platform.risk.application.RiskScoringService;
import com.engineeringproductivity.platform.risk.domain.RiskScore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implements the complete AI-driven fix loop:
 *
 * 1. Collects all findings: AI review issues + compliance gaps + risk + user instructions
 * 2. Asks the LLM to produce an improved RequirementKnowledgeModel addressing those issues
 * 3. Updates the analysis with the improved model
 * 4. Re-generates code (JavaCodeTemplates applied to improved model)
 * 5. Runs build validation on the new code
 * 6. Commits all updated files to the existing PR branch
 * 7. Re-runs compliance check on the updated PR
 * 8. Re-runs risk scoring
 * 9. Posts a summary comment on the PR
 * 10. Returns before/after comparison of all scores
 */
@Service
public class ApplyFixesService {

    private static final Logger log = LoggerFactory.getLogger(ApplyFixesService.class);
    private static final String FIX_ANALYZER_VERSION_PREFIX = "ai-fix-v1+";

    private final RequirementStoryRepository storyRepository;
    private final RequirementAnalyzerService analyzerService;
    private final RequirementAnalysisRepository analysisRepository;
    private final PullRequestRepository prRepository;
    private final GitHubClient gitHubClient;
    private final Optional<LlmGateway> llmGateway;
    private final ObjectMapper objectMapper;
    private final CodeGeneratorService codeGeneratorService;
    private final BuildValidationService buildValidationService;
    private final ComplianceService complianceService;
    private final RiskScoringService riskScoringService;
    private final AiPrReviewService aiPrReviewService;

    public ApplyFixesService(RequirementStoryRepository storyRepository,
                              RequirementAnalyzerService analyzerService,
                              RequirementAnalysisRepository analysisRepository,
                              PullRequestRepository prRepository,
                              GitHubClient gitHubClient,
                              Optional<LlmGateway> llmGateway,
                              ObjectMapper objectMapper,
                              CodeGeneratorService codeGeneratorService,
                              BuildValidationService buildValidationService,
                              ComplianceService complianceService,
                              RiskScoringService riskScoringService,
                              AiPrReviewService aiPrReviewService) {
        this.storyRepository = storyRepository;
        this.analyzerService = analyzerService;
        this.analysisRepository = analysisRepository;
        this.prRepository = prRepository;
        this.gitHubClient = gitHubClient;
        this.llmGateway = llmGateway;
        this.objectMapper = objectMapper;
        this.codeGeneratorService = codeGeneratorService;
        this.buildValidationService = buildValidationService;
        this.complianceService = complianceService;
        this.riskScoringService = riskScoringService;
        this.aiPrReviewService = aiPrReviewService;
    }

    public FixResult applyFixes(UUID requirementId, String customInstructions) {
        LlmGateway gateway = llmGateway.orElseThrow(() ->
                new IllegalStateException("Fix loop requires an LLM provider. Set ANALYZER_PROVIDER=groq/gemini/ollama."));

        RequirementStory story = storyRepository.findById(requirementId)
                .orElseThrow(() -> new ResourceNotFoundException("Requirement not found: " + requirementId));

        RequirementAnalysis analysis = analyzerService.getAnalysis(requirementId);
        RequirementKnowledgeModel currentModel = analysis.getKnowledgeModel();

        CreatedPullRequest pr = prRepository.findByRequirementId(requirementId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No PR found. Create a GitHub PR before applying fixes."));

        // Snapshot before scores
        int complianceBefore = getComplianceScore(requirementId);
        int riskBefore = getRiskScore(requirementId);
        List<AiReviewIssue> issuesBefore = getAiIssues(requirementId);

        log.info("Applying fixes for requirement={}, pr=#{}, complianceBefore={}, riskBefore={}",
                requirementId, pr.getPrNumber(), complianceBefore, riskBefore);

        // ── Step 1: Improve knowledge model via LLM ─────────────────────────
        log.info("[Fix] Step 1/6 — Improving knowledge model via LLM");
        RequirementKnowledgeModel improvedModel;
        try {
            String improved = improveModelWithLlm(gateway, story, currentModel,
                    issuesBefore, getComplianceGaps(requirementId), getRiskRecommendation(requirementId), customInstructions);
            improvedModel = parseModel(improved, currentModel);
        } catch (Exception e) {
            log.warn("[Fix] LLM model improvement failed, continuing with original model: {}", e.getMessage(), e);
            improvedModel = currentModel;
        }
        updateAnalysis(requirementId, improvedModel, gateway.modelId());

        // ── Step 2: Re-generate code ─────────────────────────────────────────
        log.info("[Fix] Step 2/6 — Re-generating code from improved model");
        GeneratedCodeBundle newBundle = codeGeneratorService.generate(requirementId);

        // ── Step 3: Build validation ─────────────────────────────────────────
        log.info("[Fix] Step 3/6 — Running build validation");
        BuildValidationReport buildReport = buildValidationService.validate(requirementId);
        if ("FAILED".equals(buildReport.getStatus())) {
            log.warn("[Fix] Build validation FAILED — committing anyway, issues will be visible");
        }

        // ── Step 4: Commit updated files to PR branch ────────────────────────
        log.info("[Fix] Step 4/6 — Committing {} files to branch {}", newBundle.getGeneratedFiles().size(), pr.getHeadBranch());
        try {
            commitFilesToBranch(newBundle, pr.getHeadBranch(), story.getExternalKey());
        } catch (Exception e) {
            log.error("[Fix] Failed to commit files to branch {}: {}", pr.getHeadBranch(), e.getMessage(), e);
            throw new RuntimeException("Failed to commit generated files to PR branch: " + e.getMessage(), e);
        }

        // ── Step 5: Re-run compliance + risk ─────────────────────────────────
        log.info("[Fix] Step 5/6 — Re-running compliance + risk scoring");
        ComplianceReport newCompliance;
        RiskScore newRisk;
        try {
            newCompliance = complianceService.check(requirementId);
        } catch (Exception e) {
            log.warn("[Fix] Compliance check failed, using previous score: {}", e.getMessage());
            newCompliance = null;
        }
        try {
            newRisk = riskScoringService.score(requirementId);
        } catch (Exception e) {
            log.warn("[Fix] Risk scoring failed, using previous score: {}", e.getMessage());
            newRisk = null;
        }

        // ── Step 6: Re-run AI review + post PR comment ───────────────────────
        log.info("[Fix] Step 6/6 — Running fresh AI review");
        AiPrReview newReview;
        try {
            newReview = aiPrReviewService.review(requirementId);
        } catch (Exception e) {
            log.warn("[Fix] AI review failed: {}", e.getMessage());
            newReview = null;
        }

        int complianceAfter = newCompliance != null ? newCompliance.getComplianceScore() : complianceBefore;
        int riskAfter = newRisk != null ? newRisk.getOverallScore() : riskBefore;
        int issuesAfter = newReview != null ? newReview.getIssues().size() : issuesBefore.size();
        String reviewSummary = newReview != null ? newReview.getSummary() : "Review skipped";
        boolean approved = newReview != null && newReview.isApproved();

        try {
            String comment = buildFixSummaryComment(
                    complianceBefore, complianceAfter,
                    riskBefore, riskAfter,
                    issuesBefore.size(), issuesAfter,
                    buildReport.getStatus(), customInstructions);
            gitHubClient.createReviewComment(pr.getPrNumber(), comment);
        } catch (Exception e) {
            log.warn("[Fix] Failed to post PR comment: {}", e.getMessage());
        }

        log.info("[Fix] Complete — compliance: {}→{}, risk: {}→{}, issues: {}→{}",
                complianceBefore, complianceAfter, riskBefore, riskAfter,
                issuesBefore.size(), issuesAfter);

        return new FixResult(
                requirementId, pr.getHtmlUrl(), pr.getPrNumber(),
                complianceBefore, complianceAfter,
                riskBefore, riskAfter,
                issuesBefore.size(), issuesAfter,
                buildReport.getStatus(), buildReport.getFileCount(),
                reviewSummary, approved,
                Instant.now()
        );
    }

    // ── LLM improvement ───────────────────────────────────────────────────────

    private String improveModelWithLlm(LlmGateway gateway, RequirementStory story,
                                        RequirementKnowledgeModel model,
                                        List<AiReviewIssue> issues, List<ComplianceGap> gaps,
                                        String riskRec, String customInstructions) {
        boolean isAlgorithm = model.resolvedType().name().equals("ALGORITHM");

        String systemPrompt = isAlgorithm ? """
                You are an expert competitive programmer and Java engineer.
                Your task: improve a RequirementKnowledgeModel for an ALGORITHMIC coding problem.

                Fix strategy:
                - REQUIREMENT_GAP / missing implementation → improve functionalRequirements to be more precise
                - Missing edge cases → add to edgeCases
                - Validation issues → add validationRules (NOT_NULL, NOT_EMPTY, RANGE, PATTERN)

                STRICT RULES:
                - Leave entities=[], apiEndpoints=[], securityRequirements=[]
                - Do NOT add Spring/JPA/REST constructs
                - functionalRequirements must describe the EXACT algorithm steps, inputs, and outputs

                Return ONLY valid JSON — no markdown, no explanation:
                {
                  "entities": [],
                  "apiEndpoints": [],
                  "validationRules": [{"targetField":"...","ruleType":"NOT_NULL|NOT_EMPTY|RANGE|PATTERN","description":"...","extractedFrom":"..."}],
                  "functionalRequirements": [{"id":"FR-001","description":"...","priority":"MUST","sourceContext":"..."}],
                  "securityRequirements": [],
                  "edgeCases": [{"condition":"...","expectedBehavior":"...","sourceContext":"..."}]
                }
                """ : """
                You are an expert Java Spring Boot architect.
                Your task: improve a RequirementKnowledgeModel to fix all reported issues.

                Fix strategy:
                - SECURITY issues → add securityRequirements entries (authentication, field encryption, RBAC)
                - VALIDATION issues → add validationRules with correct ruleType (MAX_LENGTH, MIN_LENGTH, POSITIVE, NOT_NULL, PATTERN)
                - REQUIREMENT_GAP → add missing functionalRequirements or apiEndpoints
                - CODE_SMELL / ARCHITECTURE → improve entity field design or add missing abstractions

                PATTERN ruleType rules:
                - Use ruleType "PATTERN" for alphanumeric-only fields
                - Always include the regex in the description, e.g. "Must match ^[a-zA-Z0-9]+$ (no spaces or special characters)"

                Return ONLY valid JSON — no markdown, no explanation:
                {
                  "entities": [{"name":"...","fields":[{"name":"...","inferredType":"String|UUID|Integer|Long|BigDecimal|Boolean|Instant","required":true,"constraints":["UNIQUE","NOT_NULL"]}]}],
                  "apiEndpoints": [{"httpMethod":"POST","suggestedPath":"/api/v1/...","description":"...","requiresAuth":false}],
                  "validationRules": [{"targetField":"...","ruleType":"MAX_LENGTH|MIN_LENGTH|POSITIVE|NOT_NULL","description":"...","extractedFrom":"..."}],
                  "functionalRequirements": [{"id":"FR-001","description":"...","priority":"MUST","sourceContext":"..."}],
                  "securityRequirements": [{"category":"AUTHENTICATION|AUTHORIZATION|ENCRYPTION|INPUT_VALIDATION","description":"..."}],
                  "edgeCases": [{"condition":"...","expectedBehavior":"...","sourceContext":"..."}]
                }
                """;

        String issuesSummary = issues.isEmpty() ? "None" :
                issues.stream().map(i -> "  [%s/%s] %s → %s".formatted(i.severity(), i.category(), i.description(), i.suggestion()))
                        .reduce("", (a, b) -> a + "\n" + b);

        String gapsSummary = gaps.isEmpty() ? "None" :
                gaps.stream().map(g -> "  [%s] %s (expected: %s)".formatted(g.severity(), g.description(), g.expected()))
                        .reduce("", (a, b) -> a + "\n" + b);

        String userPrompt;
        try {
            String modelJson = objectMapper.writeValueAsString(model);
            userPrompt = """
                    === REQUIREMENT ===
                    Title: %s
                    Description: %s
                    Acceptance Criteria: %s

                    === CURRENT KNOWLEDGE MODEL ===
                    %s

                    === AI REVIEW ISSUES TO FIX ===
                    %s

                    === COMPLIANCE GAPS TO FIX ===
                    %s

                    === RISK RECOMMENDATION ===
                    %s

                    === USER CUSTOM INSTRUCTIONS ===
                    %s

                    Produce an improved RequirementKnowledgeModel. Keep all existing correct content, only add/fix what's needed.
                    Return JSON only.
                    """.formatted(
                    story.getTitle(),
                    story.getDescription(),
                    String.join("; ", story.getAcceptanceCriteria()),
                    modelJson,
                    issuesSummary,
                    gapsSummary,
                    riskRec != null ? riskRec : "None",
                    customInstructions != null && !customInstructions.isBlank() ? customInstructions : "None"
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize model for LLM prompt", e);
        }

        return gateway.complete(systemPrompt, userPrompt);
    }

    private RequirementKnowledgeModel parseModel(String raw, RequirementKnowledgeModel fallback) {
        try {
            String cleaned = raw.strip();
            if (cleaned.startsWith("```")) {
                int nl = cleaned.indexOf('\n');
                int end = cleaned.lastIndexOf("```");
                if (nl > 0 && end > nl) cleaned = cleaned.substring(nl + 1, end).strip();
            }
            int start = cleaned.indexOf('{');
            int end = cleaned.lastIndexOf('}');
            if (start >= 0 && end > start) cleaned = cleaned.substring(start, end + 1);
            return objectMapper.readValue(cleaned, RequirementKnowledgeModel.class);
        } catch (Exception e) {
            log.error("Failed to parse improved model, using original: {}", e.getMessage());
            return fallback;
        }
    }

    @Transactional
    public void updateAnalysis(UUID requirementId, RequirementKnowledgeModel improved, String modelId) {
        analysisRepository.findByRequirementId(requirementId).ifPresent(a -> {
            RequirementKnowledgeModel original = a.getKnowledgeModel();
            // Always preserve requirementType and generationPlan — the fix-loop LLM prompt
            // doesn't include these fields so they come back null from the LLM response.
            String reqType = improved.requirementType() != null
                    ? improved.requirementType()
                    : original.requirementType();
            GenerationPlan plan = improved.generationPlan() != null
                    ? improved.generationPlan()
                    : original.generationPlan();

            // For ALGORITHM/CLI/LIBRARY types, enforce that CRUD artifacts are never stored
            // even if the improvement LLM accidentally returned them.
            boolean isNonCrud = "ALGORITHM".equals(reqType) || "CLI_APPLICATION".equals(reqType) || "LIBRARY".equals(reqType);
            RequirementKnowledgeModel withType = new RequirementKnowledgeModel(
                    isNonCrud ? List.of() : improved.entities(),
                    isNonCrud ? List.of() : improved.apiEndpoints(),
                    improved.validationRules(),
                    improved.functionalRequirements(),
                    isNonCrud ? List.of() : improved.securityRequirements(),
                    improved.edgeCases(), reqType, plan
            );
            a.refresh(withType, FIX_ANALYZER_VERSION_PREFIX + modelId);
            analysisRepository.save(a);
        });
    }

    // ── GitHub commit ─────────────────────────────────────────────────────────

    private void commitFilesToBranch(GeneratedCodeBundle bundle, String branch, String externalKey) {
        for (var file : bundle.getGeneratedFiles()) {
            String path = "src/main/java/" + file.fullPath();
            gitHubClient.upsertFile(branch, path, file.content(),
                    "fix: AI-driven improvements for " + externalKey + " [" + file.fileType().name() + "]");
        }
    }

    // ── Score helpers ─────────────────────────────────────────────────────────

    private int getComplianceScore(UUID id) {
        try { return complianceService.getReport(id).getComplianceScore(); }
        catch (Exception e) { return 0; }
    }

    private int getRiskScore(UUID id) {
        try { return riskScoringService.getRiskScore(id).getOverallScore(); }
        catch (Exception e) { return 0; }
    }

    private List<AiReviewIssue> getAiIssues(UUID id) {
        try { return aiPrReviewService.getLatestReview(id).getIssues(); }
        catch (Exception e) { return List.of(); }
    }

    private List<ComplianceGap> getComplianceGaps(UUID id) {
        try { return complianceService.getReport(id).getGaps(); }
        catch (Exception e) { return List.of(); }
    }

    private String getRiskRecommendation(UUID id) {
        try { return riskScoringService.getRiskScore(id).getRecommendation(); }
        catch (Exception e) { return null; }
    }

    // ── PR comment ────────────────────────────────────────────────────────────

    private String buildFixSummaryComment(int compBefore, int compAfter,
                                           int riskBefore, int riskAfter,
                                           int issuesBefore, int issuesAfter,
                                           String buildStatus, String customInstructions) {
        String trend = compAfter > compBefore ? "📈 improved" : compAfter == compBefore ? "↔ unchanged" : "⚠️ decreased";
        return """
                ## 🤖 AI Fix Loop Applied

                The AI Fix Loop has re-generated and committed improved code to this branch.

                | Metric | Before | After | Trend |
                |---|---|---|---|
                | Compliance Score | %d/100 | %d/100 | %s |
                | Risk Score | %d/100 | %d/100 | %s |
                | AI Review Issues | %d | %d | %s |
                | Build Validation | — | %s | — |

                %s

                *Generated by AI Engineering Productivity Platform*
                """.formatted(
                compBefore, compAfter, trend,
                riskBefore, riskAfter, riskAfter <= riskBefore ? "📉 reduced" : "⚠️ increased",
                issuesBefore, issuesAfter, issuesAfter <= issuesBefore ? "📉 fewer" : "⚠️ more",
                buildStatus,
                customInstructions != null && !customInstructions.isBlank()
                        ? "\n**Custom instructions applied:** " + customInstructions : ""
        );
    }

    // ── Response DTO ──────────────────────────────────────────────────────────

    public record FixResult(
            UUID requirementId,
            String prUrl,
            int prNumber,
            int complianceBefore,
            int complianceAfter,
            int riskBefore,
            int riskAfter,
            int issuesBefore,
            int issuesAfter,
            String buildStatus,
            int fileCount,
            String newReviewSummary,
            boolean approved,
            Instant appliedAt
    ) {}
}
