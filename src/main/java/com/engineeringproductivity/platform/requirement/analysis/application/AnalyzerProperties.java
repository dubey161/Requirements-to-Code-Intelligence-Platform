package com.engineeringproductivity.platform.requirement.analysis.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for the requirement analyzer.
 *
 * <pre>
 * analyzer:
 *   provider: gemini          # rule-based | gemini | groq | ollama
 *   fallback-to-rule-based: true
 *   gemini:
 *     api-key: ${GEMINI_API_KEY}
 *     model: gemini-2.0-flash
 *   groq:
 *     api-key: ${GROQ_API_KEY}
 *     model: llama-3.3-70b-versatile
 *   ollama:
 *     base-url: http://localhost:11434
 *     model: llama3.2
 * </pre>
 */
@ConfigurationProperties(prefix = "analyzer")
public record AnalyzerProperties(
        @DefaultValue("rule-based") String provider,
        @DefaultValue("true") boolean fallbackToRuleBased,
        GeminiProperties gemini,
        GroqProperties groq,
        OllamaProperties ollama
) {

    public record GeminiProperties(
            String apiKey,
            @DefaultValue("gemini-2.0-flash") String model,
            @DefaultValue("30") int timeoutSeconds
    ) {}

    public record GroqProperties(
            String apiKey,
            @DefaultValue("llama-3.3-70b-versatile") String model,
            @DefaultValue("30") int timeoutSeconds
    ) {}

    public record OllamaProperties(
            @DefaultValue("http://localhost:11434") String baseUrl,
            @DefaultValue("llama3.2") String model,
            @DefaultValue("120") int timeoutSeconds
    ) {}
}
