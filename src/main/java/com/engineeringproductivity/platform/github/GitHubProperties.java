package com.engineeringproductivity.platform.github;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * GitHub connection config.
 * Token: https://github.com/settings/tokens → Fine-grained PAT with repo:write scope
 *
 * <pre>
 * github:
 *   token: ${GITHUB_TOKEN}
 *   owner: your-username-or-org
 *   repo: your-target-repo
 *   base-branch: main
 * </pre>
 */
@ConfigurationProperties(prefix = "github")
public record GitHubProperties(
        String token,
        String owner,
        String repo,
        @DefaultValue("main") String baseBranch
) {}
