package com.engineeringproductivity.platform.requirement.analysis.application.gateway;

import com.engineeringproductivity.platform.requirement.analysis.application.AnalyzerProperties;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

/**
 * Calls Google Gemini API (free tier: 15 RPM / 1500 req/day — no credit card required).
 * Get key at: https://aistudio.google.com/app/apikey
 *
 * Uses {@code responseMimeType: "application/json"} to force structured output.
 */
public class GeminiLlmGateway implements LlmGateway {

    private static final String BASE_URL = "https://generativelanguage.googleapis.com";

    private final AnalyzerProperties.GeminiProperties config;
    private final RestClient restClient;

    public GeminiLlmGateway(AnalyzerProperties.GeminiProperties config, RestClient restClient) {
        this.config = config;
        this.restClient = restClient;
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        return call(systemPrompt, userPrompt, "application/json");
    }

    @Override
    public String completeText(String systemPrompt, String userPrompt) {
        return call(systemPrompt, userPrompt, "text/plain");
    }

    private String call(String systemPrompt, String userPrompt, String mimeType) {
        String url = BASE_URL + "/v1beta/models/" + config.model() + ":generateContent?key=" + config.apiKey();

        GeminiRequest request = new GeminiRequest(
                new SystemInstruction(List.of(new Part(systemPrompt))),
                List.of(new Content("user", List.of(new Part(userPrompt)))),
                new GenerationConfig(mimeType, 0.1, 4096)
        );

        GeminiResponse response = restClient.post()
                .uri(url)
                .header("Content-Type", "application/json")
                .body(request)
                .retrieve()
                .body(GeminiResponse.class);

        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            throw new LlmGatewayException("Gemini returned empty response");
        }

        var parts = response.candidates().get(0).content().parts();
        if (parts == null || parts.isEmpty()) {
            throw new LlmGatewayException("Gemini response has no parts");
        }

        return parts.get(0).text();
    }

    @Override
    public String modelId() {
        return config.model();
    }

    // -------------------------------------------------------------------------
    // Request / Response DTOs (inner records — scope is this gateway only)
    // -------------------------------------------------------------------------

    record GeminiRequest(
            SystemInstruction systemInstruction,
            List<Content> contents,
            GenerationConfig generationConfig
    ) {}

    record SystemInstruction(List<Part> parts) {}

    record Content(String role, List<Part> parts) {}

    record Part(String text) {}

    record GenerationConfig(
            String responseMimeType,
            double temperature,
            int maxOutputTokens
    ) {}

    record GeminiResponse(List<Candidate> candidates, UsageMetadata usageMetadata) {}

    record Candidate(Content content, String finishReason) {}

    record UsageMetadata(int promptTokenCount, int candidatesTokenCount, int totalTokenCount) {}
}
