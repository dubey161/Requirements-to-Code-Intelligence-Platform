package com.engineeringproductivity.platform.github.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pull_request")
public class CreatedPullRequest {

    @Id
    private UUID id;

    @Column(name = "requirement_id", nullable = false, unique = true)
    private UUID requirementId;

    @Column(name = "pr_number", nullable = false)
    private int prNumber;

    @Column(name = "html_url", nullable = false, length = 500)
    private String htmlUrl;

    @Column(name = "head_branch", nullable = false, length = 200)
    private String headBranch;

    @Column(name = "head_sha", nullable = false, length = 100)
    private String headSha;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CreatedPullRequest() {}

    private CreatedPullRequest(UUID id, UUID requirementId, int prNumber,
                                String htmlUrl, String headBranch, String headSha, Instant createdAt) {
        this.id = id;
        this.requirementId = requirementId;
        this.prNumber = prNumber;
        this.htmlUrl = htmlUrl;
        this.headBranch = headBranch;
        this.headSha = headSha;
        this.createdAt = createdAt;
    }

    public static CreatedPullRequest create(UUID requirementId, int prNumber,
                                             String htmlUrl, String headBranch, String headSha) {
        return new CreatedPullRequest(UUID.randomUUID(), requirementId, prNumber,
                htmlUrl, headBranch, headSha, Instant.now());
    }

    public UUID getId() { return id; }
    public UUID getRequirementId() { return requirementId; }
    public int getPrNumber() { return prNumber; }
    public String getHtmlUrl() { return htmlUrl; }
    public String getHeadBranch() { return headBranch; }
    public String getHeadSha() { return headSha; }
    public Instant getCreatedAt() { return createdAt; }
}
