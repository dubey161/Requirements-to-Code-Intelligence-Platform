package com.engineeringproductivity.platform.requirement.analysis.application.gateway;

/**
 * Thin abstraction over any LLM provider.
 * Implementations handle HTTP transport, auth, and request/response mapping.
 */
public interface LlmGateway {

    /**
     * Sends a prompt to the LLM and returns structured JSON.
     * Implementations must force JSON output mode (response_format, responseMimeType, etc.).
     */
    String complete(String systemPrompt, String userPrompt);

    /**
     * Sends a prompt to the LLM and returns free-form text (e.g. source code).
     * Implementations must NOT force JSON output mode.
     * Default delegates to {@link #complete} — override when JSON mode is hard-coded.
     */
    default String completeText(String systemPrompt, String userPrompt) {
        return complete(systemPrompt, userPrompt);
    }

    /** Human-readable identifier used in analyzerVersion field, e.g. "gemini-2.0-flash". */
    String modelId();
}
