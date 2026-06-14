package com.engineeringproductivity.platform.jira;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Jira Cloud connection config.
 *
 * <pre>
 * jira:
 *   base-url: https://yourorg.atlassian.net
 *   email: you@company.com
 *   api-token: ${JIRA_API_TOKEN}
 *   acceptance-criteria-field: customfield_10016
 * </pre>
 *
 * Free tier: Jira Free supports up to 10 users and the REST API works fully.
 * API token: https://id.atlassian.com/manage-profile/security/api-tokens
 */
@ConfigurationProperties(prefix = "jira")
public record JiraProperties(
        String baseUrl,
        String email,
        String apiToken,
        @DefaultValue("customfield_10016") String acceptanceCriteriaField
) {}
