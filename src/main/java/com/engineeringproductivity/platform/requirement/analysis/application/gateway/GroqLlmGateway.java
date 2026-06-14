package com.engineeringproductivity.platform.requirement.analysis.application.gateway;

import com.engineeringproductivity.platform.requirement.analysis.application.AnalyzerProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Calls Groq Cloud API — OpenAI-compatible, free tier (30 RPM / 14,400 req/day).
 * Get key at: https://console.groq.com/keys
 *
 * Uses {@code response_format: {type: "json_object"}} for structured output.
 */
public class GroqLlmGateway implements LlmGateway {

    private static final String BASE_URL = "https://api.groq.com";

    private final AnalyzerProperties.GroqProperties config;
    private final RestClient restClient;

    public GroqLlmGateway(AnalyzerProperties.GroqProperties config, RestClient restClient) {
        this.config = config;
        this.restClient = restClient;
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        return call(systemPrompt, userPrompt, new ResponseFormat("json_object"));
    }

    @Override
    public String completeText(String systemPrompt, String userPrompt) {
        return call(systemPrompt, userPrompt, null);
    }

    private String call(String systemPrompt, String userPrompt, ResponseFormat responseFormat) {
        GroqRequest request = new GroqRequest(
                config.model(),
                List.of(
                        new Message("system", systemPrompt),
                        new Message("user", userPrompt)
                ),
                0.1,
                4096,
                false,
                responseFormat
        );

        GroqResponse response = restClient.post()
                .uri(BASE_URL + "/openai/v1/chat/completions")
                .header("Authorization", "Bearer " + config.apiKey())
                .header("Content-Type", "application/json")
                .body(request)
                .retrieve()
                .body(GroqResponse.class);

        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new LlmGatewayException("Groq returned empty response");
        }

        String content = response.choices().get(0).message().content();
        if (content == null || content.isBlank()) {
            throw new LlmGatewayException("Groq response has no content");
        }
        return content;
    }

    @Override
    public String modelId() {
        return config.model();
    }

    // -------------------------------------------------------------------------
    // DTOs
    // -------------------------------------------------------------------------

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record GroqRequest(
            String model,
            List<Message> messages,
            double temperature,
            @JsonProperty("max_tokens") int maxTokens,
            boolean stream,
            @JsonProperty("response_format") ResponseFormat responseFormat
    ) {}

    record Message(String role, String content) {}

    record ResponseFormat(String type) {}

    record GroqResponse(List<Choice> choices, Usage usage) {}

    record Choice(Message message, @JsonProperty("finish_reason") String finishReason) {}

    record Usage(
            @JsonProperty("prompt_tokens") int promptTokens,
            @JsonProperty("completion_tokens") int completionTokens,
            @JsonProperty("total_tokens") int totalTokens
    ) {}
}
