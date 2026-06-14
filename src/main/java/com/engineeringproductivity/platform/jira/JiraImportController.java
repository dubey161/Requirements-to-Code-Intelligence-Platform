package com.engineeringproductivity.platform.jira;

import com.engineeringproductivity.platform.auth.domain.User;
import com.engineeringproductivity.platform.requirement.api.RequirementResponse;
import com.engineeringproductivity.platform.requirement.domain.RequirementStory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Imports a Jira issue into the platform.
 *
 * Flow: Jira API → RequirementStory → RequirementAnalysis (auto-triggered)
 *
 * POST /api/v1/requirements/import/jira/PROJ-123
 */
@RestController
@RequestMapping("/api/v1/requirements/import/jira")
public class JiraImportController {

    private final JiraIssueImporter importer;

    public JiraImportController(JiraIssueImporter importer) {
        this.importer = importer;
    }

    @PostMapping("/{issueKey}")
    @ResponseStatus(HttpStatus.CREATED)
    RequirementResponse importFromJira(@PathVariable String issueKey,
                                       @AuthenticationPrincipal User currentUser) {
        RequirementStory story = importer.importIssue(issueKey, currentUser.getId());
        return RequirementResponse.from(story);
    }
}
