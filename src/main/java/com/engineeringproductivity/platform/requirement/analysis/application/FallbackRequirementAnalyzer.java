package com.engineeringproductivity.platform.requirement.analysis.application;

import com.engineeringproductivity.platform.requirement.analysis.domain.model.RequirementKnowledgeModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Decorator that tries the primary analyzer (LLM) and falls back to
 * the rule-based analyzer on any failure. Guarantees analysis always completes.
 */
public class FallbackRequirementAnalyzer implements RequirementAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(FallbackRequirementAnalyzer.class);

    private final RequirementAnalyzer primary;
    private final RuleBasedRequirementAnalyzer fallback;

    public FallbackRequirementAnalyzer(RequirementAnalyzer primary, RuleBasedRequirementAnalyzer fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    @Override
    public RequirementKnowledgeModel analyze(String title, String description, List<String> acceptanceCriteria) {
        try {
            RequirementKnowledgeModel result = primary.analyze(title, description, acceptanceCriteria);
            log.debug("LLM analysis succeeded: version={}", primary.version());
            return result;
        } catch (Exception e) {
            log.warn("LLM analysis failed ({}), falling back to rule-based: {}", primary.version(), e.getMessage());
            return fallback.analyze(title, description, acceptanceCriteria);
        }
    }

    /** Type-aware analysis — passes requirementType to the LLM prompt for better extraction. */
    public RequirementKnowledgeModel analyzeWithType(String title, String description,
                                                      List<String> acceptanceCriteria,
                                                      String requirementType) {
        try {
            if (primary instanceof LlmRequirementAnalyzer llm) {
                RequirementKnowledgeModel result = llm.analyze(title, description, acceptanceCriteria, requirementType);
                log.debug("Type-aware LLM analysis succeeded: type={}", requirementType);
                return result;
            }
            return primary.analyze(title, description, acceptanceCriteria);
        } catch (Exception e) {
            log.warn("Type-aware LLM analysis failed ({}), falling back to rule-based: {}",
                    primary.version(), e.getMessage());
            return fallback.analyze(title, description, acceptanceCriteria);
        }
    }

    @Override
    public String version() {
        return primary.version() + "+fallback(" + fallback.version() + ")";
    }
}
