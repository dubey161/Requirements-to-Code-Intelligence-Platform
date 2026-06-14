package com.engineeringproductivity.platform.github;

import com.engineeringproductivity.platform.github.domain.CreatedPullRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/requirements/{requirementId}/pull-request")
public class GitHubController {

    private final GitHubPrService prService;

    public GitHubController(GitHubPrService prService) {
        this.prService = prService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    PullRequestResponse createPr(@PathVariable UUID requirementId) {
        return PullRequestResponse.from(prService.createPr(requirementId));
    }

    @GetMapping
    PullRequestResponse getPr(@PathVariable UUID requirementId) {
        return PullRequestResponse.from(prService.getPr(requirementId));
    }

    public record PullRequestResponse(
            UUID id, UUID requirementId, int prNumber,
            String htmlUrl, String headBranch, String headSha, Instant createdAt
    ) {
        static PullRequestResponse from(CreatedPullRequest pr) {
            return new PullRequestResponse(pr.getId(), pr.getRequirementId(), pr.getPrNumber(),
                    pr.getHtmlUrl(), pr.getHeadBranch(), pr.getHeadSha(), pr.getCreatedAt());
        }
    }
}
