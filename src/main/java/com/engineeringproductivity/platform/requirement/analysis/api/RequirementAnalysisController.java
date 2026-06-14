package com.engineeringproductivity.platform.requirement.analysis.api;

import com.engineeringproductivity.platform.requirement.analysis.application.RequirementAnalyzerService;
import com.engineeringproductivity.platform.requirement.analysis.domain.RequirementAnalysis;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/requirements/{requirementId}/analysis")
public class RequirementAnalysisController {

    private final RequirementAnalyzerService analyzerService;

    public RequirementAnalysisController(RequirementAnalyzerService analyzerService) {
        this.analyzerService = analyzerService;
    }

    /**
     * Triggers (or re-triggers) analysis on a requirement.
     * Returns 202 Accepted with the resulting analysis.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    RequirementAnalysisResponse triggerAnalysis(@PathVariable UUID requirementId) {
        RequirementAnalysis analysis = analyzerService.analyze(requirementId);
        return RequirementAnalysisResponse.from(analysis);
    }

    /**
     * Returns the existing analysis for a requirement.
     * Returns 404 if analysis has not been run yet.
     */
    @GetMapping
    RequirementAnalysisResponse getAnalysis(@PathVariable UUID requirementId) {
        RequirementAnalysis analysis = analyzerService.getAnalysis(requirementId);
        return RequirementAnalysisResponse.from(analysis);
    }
}
