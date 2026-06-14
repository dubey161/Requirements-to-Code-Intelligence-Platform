package com.engineeringproductivity.platform.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Calls Gemini text-embedding-004 (free tier) to produce 768-dimensional float vectors.
 * Returns an empty float[] if the API key is not configured or the call fails —
 * all callers must gracefully handle the empty case.
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final String apiKey;
    private final RestClient restClient;

    public EmbeddingService(@Value("${analyzer.gemini.api-key:}") String apiKey) {
        this.apiKey = apiKey;
        this.restClient = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com")
                .build();
    }

    /**
     * Generates a 768-dim embedding for the given text.
     * Returns float[0] if not configured or on error — never throws.
     */
    public float[] embed(String text) {
        if (apiKey == null || apiKey.isBlank()) {
            return new float[0];
        }

        // Truncate to avoid hitting free-tier input limits
        String input = text.length() > 8000 ? text.substring(0, 8000) : text;

        try {
            var body = Map.of(
                    "model", "models/text-embedding-004",
                    "content", Map.of("parts", List.of(Map.of("text", input)))
            );

            EmbeddingResponse response = restClient.post()
                    .uri("/v1beta/models/text-embedding-004:embedContent?key={key}", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(EmbeddingResponse.class);

            if (response != null && response.embedding() != null && response.embedding().values() != null) {
                List<Float> values = response.embedding().values();
                float[] result = new float[values.size()];
                for (int i = 0; i < values.size(); i++) {
                    result[i] = values.get(i);
                }
                return result;
            }
        } catch (Exception e) {
            log.warn("Embedding call failed: {}", e.getMessage());
        }

        return new float[0];
    }

    record EmbeddingResponse(EmbeddingData embedding) {}

    record EmbeddingData(List<Float> values) {}
}
