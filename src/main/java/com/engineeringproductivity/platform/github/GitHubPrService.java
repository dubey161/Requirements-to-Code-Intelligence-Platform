package com.engineeringproductivity.platform.github;

import com.engineeringproductivity.platform.codegen.application.CodeGeneratorService;
import com.engineeringproductivity.platform.codegen.application.CompileValidationService;
import com.engineeringproductivity.platform.codegen.domain.GeneratedCodeBundle;
import com.engineeringproductivity.platform.codegen.domain.GeneratedFile;
import com.engineeringproductivity.platform.common.api.ResourceNotFoundException;
import com.engineeringproductivity.platform.github.domain.CreatedPullRequest;
import com.engineeringproductivity.platform.github.domain.PullRequestRepository;
import com.engineeringproductivity.platform.requirement.domain.RequirementStory;
import com.engineeringproductivity.platform.requirement.domain.RequirementStoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Orchestrates the full GitHub PR creation workflow:
 * 1. Get generated code bundle
 * 2. Create feature branch
 * 3. Commit all generated files
 * 4. Open PR with requirement context
 */
@Service
public class GitHubPrService {

    private static final Logger log = LoggerFactory.getLogger(GitHubPrService.class);

    private final GitHubClient gitHub;
    private final CodeGeneratorService codeGeneratorService;
    private final CompileValidationService compileValidationService;
    private final RequirementStoryRepository storyRepository;
    private final PullRequestRepository prRepository;

    public GitHubPrService(GitHubClient gitHub,
                            CodeGeneratorService codeGeneratorService,
                            CompileValidationService compileValidationService,
                            RequirementStoryRepository storyRepository,
                            PullRequestRepository prRepository) {
        this.gitHub = gitHub;
        this.codeGeneratorService = codeGeneratorService;
        this.compileValidationService = compileValidationService;
        this.storyRepository = storyRepository;
        this.prRepository = prRepository;
    }

    @Transactional
    public CreatedPullRequest createPr(UUID requirementId) {
        RequirementStory story = storyRepository.findById(requirementId)
                .orElseThrow(() -> new ResourceNotFoundException("Requirement not found: " + requirementId));

        GeneratedCodeBundle bundle = codeGeneratorService.getBundle(requirementId);

        // Validate syntax before touching GitHub — fail fast, don't push broken code
        compileValidationService.validate(bundle.getGeneratedFiles());
        log.info("Compile validation passed for requirement: {}", requirementId);

        String branchName = buildBranchName(story.getExternalKey(), story.getTitle());

        // Create branch from current HEAD of base branch
        String baseSha = gitHub.getBaseBranchSha();
        gitHub.createBranch(branchName, baseSha);

        // Commit each generated file
        for (GeneratedFile file : bundle.getGeneratedFiles()) {
            String filePath = "src/main/java/" + file.fullPath();
            log.info("Committing: {}", filePath);
            gitHub.upsertFile(branchName, filePath, file.content(),
                    "feat(%s): add generated %s".formatted(story.getExternalKey(), file.fileName()));
        }

        // Open PR
        String prTitle = "[%s] %s".formatted(story.getExternalKey(), story.getTitle());
        String prBody = buildPrDescription(story, bundle);
        GitHubClient.CreatedPr createdPr = gitHub.createPullRequest(prTitle, prBody, branchName);

        CreatedPullRequest pr = CreatedPullRequest.create(
                requirementId, createdPr.number(), createdPr.htmlUrl(),
                createdPr.headBranch(), createdPr.headSha());

        return prRepository.save(pr);
    }

    @Transactional(readOnly = true)
    public CreatedPullRequest getPr(UUID requirementId) {
        return prRepository.findByRequirementId(requirementId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No PR found for requirement: " + requirementId +
                        ". Trigger PR creation via POST /api/v1/requirements/{id}/pull-request"));
    }

    private String buildBranchName(String externalKey, String title) {
        String slug = title.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-{2,}", "-");
        String truncated = slug.length() > 40 ? slug.substring(0, 40) : slug;
        return "feat/" + externalKey.toLowerCase() + "-" + truncated;
    }

    private String buildPrDescription(RequirementStory story, GeneratedCodeBundle bundle) {
        StringBuilder sb = new StringBuilder();
        sb.append("## ").append(story.getTitle()).append("\n\n");
        sb.append("**Jira:** ").append(story.getExternalKey()).append("\n\n");
        sb.append("### Description\n").append(story.getDescription()).append("\n\n");

        if (!story.getAcceptanceCriteria().isEmpty()) {
            sb.append("### Acceptance Criteria\n");
            story.getAcceptanceCriteria().forEach(ac -> sb.append("- ").append(ac).append("\n"));
            sb.append("\n");
        }

        sb.append("### Generated Files\n");
        bundle.getGeneratedFiles().forEach(f ->
                sb.append("- `").append(f.fullPath()).append("` (").append(f.fileType()).append(")\n"));

        sb.append("\n---\n*Generated by AI Engineering Productivity Platform*");
        return sb.toString();
    }
}
