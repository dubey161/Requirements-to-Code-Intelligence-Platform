package com.engineeringproductivity.platform.requirement.analysis.application.gateway;

import com.engineeringproductivity.platform.requirement.analysis.application.AnalyzerProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Calls local Ollama instance — completely free, unlimited, runs on your machine.
 * Install: https://ollama.com  then run: ollama pull llama3.2
 *
 * Uses the Ollama /api/chat endpoint with format=json for structured output.
 */
public class OllamaLlmGateway implements LlmGateway {

    private final AnalyzerProperties.OllamaProperties config;
    private final RestClient restClient;

    public OllamaLlmGateway(AnalyzerProperties.OllamaProperties config, RestClient restClient) {
        this.config = config;
        this.restClient = restClient;
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        return call(systemPrompt, userPrompt, "json");
    }

    @Override
    public String completeText(String systemPrompt, String userPrompt) {
        return call(systemPrompt, userPrompt, null);
    }

    private String call(String systemPrompt, String userPrompt, String format) {
        String baseUrl = config.baseUrl() != null ? config.baseUrl() : "http://localhost:11434";

        OllamaRequest request = new OllamaRequest(
                config.model(),
                List.of(
                        new Message("system", systemPrompt),
                        new Message("user", userPrompt)
                ),
                false,
                format,
                new Options(0.1)
        );

        OllamaResponse response = restClient.post()
                .uri(baseUrl + "/api/chat")
                .header("Content-Type", "application/json")
                .body(request)
                .retrieve()
                .body(OllamaResponse.class);

        if (response == null || response.message() == null) {
            throw new LlmGatewayException("Ollama returned empty response");
        }

        String content = response.message().content();
        if (content == null || content.isBlank()) {
            throw new LlmGatewayException("Ollama response has no content");
        }
        return content;
    }

    @Override
    public String modelId() {
        return "ollama-" + config.model();
    }

    // -------------------------------------------------------------------------
    // DTOs
    // -------------------------------------------------------------------------

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record OllamaRequest(
            String model,
            List<Message> messages,
            boolean stream,
            String format,
            Options options
    ) {}

    record Message(String role, String content) {}

    record Options(double temperature) {}

    record OllamaResponse(Message message, boolean done, String doneReason) {}
}
