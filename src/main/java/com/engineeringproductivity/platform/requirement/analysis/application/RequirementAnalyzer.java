package com.engineeringproductivity.platform.requirement.analysis.application;

import com.engineeringproductivity.platform.requirement.analysis.domain.model.RequirementKnowledgeModel;

import java.util.List;

/**
 * Strategy interface for requirement analysis.
 * <p>
 * Implementations:
 * <ul>
 *   <li>{@link RuleBasedRequirementAnalyzer} — regex/heuristic, zero infra cost</li>
 *   <li>{@link LlmRequirementAnalyzer} — LLM-backed (Gemini / Groq / Ollama)</li>
 *   <li>{@link FallbackRequirementAnalyzer} — tries LLM, falls back to rule-based</li>
 * </ul>
 * The output contract ({@link RequirementKnowledgeModel}) is identical regardless of impl.
 */
public interface RequirementAnalyzer {

    RequirementKnowledgeModel analyze(String title, String description, List<String> acceptanceCriteria);

    String version();
}
