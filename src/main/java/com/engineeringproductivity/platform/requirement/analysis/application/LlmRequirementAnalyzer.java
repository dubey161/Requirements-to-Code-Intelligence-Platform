package com.engineeringproductivity.platform.requirement.analysis.application;

import com.engineeringproductivity.platform.requirement.analysis.application.gateway.LlmGateway;
import com.engineeringproductivity.platform.requirement.analysis.domain.model.RequirementKnowledgeModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * LLM-backed requirement analyzer.
 * Sends a carefully engineered prompt and parses the structured JSON response
 * into a RequirementKnowledgeModel.
 *
 * The output contract is identical to RuleBasedRequirementAnalyzer —
 * downstream services (code generation, compliance, risk) work unchanged.
 */
public class LlmRequirementAnalyzer implements RequirementAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(LlmRequirementAnalyzer.class);

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final LlmGateway gateway;
    private final PromptBuilder promptBuilder;

    public LlmRequirementAnalyzer(LlmGateway gateway, PromptBuilder promptBuilder) {
        this.gateway = gateway;
        this.promptBuilder = promptBuilder;
    }

    @Override
    public RequirementKnowledgeModel analyze(String title, String description, List<String> acceptanceCriteria) {
        return analyze(title, description, acceptanceCriteria, null);
    }

    public RequirementKnowledgeModel analyze(String title, String description,
                                              List<String> acceptanceCriteria, String requirementType) {
        PromptBuilder.PromptInput prompt = promptBuilder.build(title, description, acceptanceCriteria, requirementType);

        log.debug("Sending analysis request to LLM: model={}", gateway.modelId());

        String rawResponse = gateway.complete(prompt.systemPrompt(), prompt.userPrompt());

        log.debug("LLM raw response length={} chars", rawResponse.length());

        return parseResponse(rawResponse);
    }

    @Override
    public String version() {
        return "llm-" + gateway.modelId() + "-v1";
    }

    private RequirementKnowledgeModel parseResponse(String rawResponse) {
        String json = extractJson(rawResponse);
        try {
            return MAPPER.readValue(json, RequirementKnowledgeModel.class);
        } catch (Exception e) {
            log.error("Failed to parse LLM response as RequirementKnowledgeModel. Raw: {}", json, e);
            throw new LlmParseException(
                    "LLM returned invalid JSON for RequirementKnowledgeModel: " + e.getMessage(), e);
        }
    }

    /**
     * Some models wrap their JSON in markdown code fences even when instructed not to.
     * This strips any wrapping before parsing.
     */
    private String extractJson(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new LlmParseException("LLM returned blank response");
        }

        String trimmed = raw.strip();

        // Strip ```json ... ``` or ``` ... ```
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                trimmed = trimmed.substring(firstNewline + 1, lastFence).strip();
            }
        }

        // Find the outermost JSON object
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start < 0 || end < 0 || end <= start) {
            throw new LlmParseException("LLM response does not contain a JSON object. Response: " + trimmed);
        }

        return trimmed.substring(start, end + 1);
    }

    static class LlmParseException extends RuntimeException {
        LlmParseException(String message) { super(message); }
        LlmParseException(String message, Throwable cause) { super(message, cause); }
    }
}
