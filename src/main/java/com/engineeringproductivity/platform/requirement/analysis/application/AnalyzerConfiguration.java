package com.engineeringproductivity.platform.requirement.analysis.application;

import com.engineeringproductivity.platform.requirement.analysis.application.gateway.GeminiLlmGateway;
import com.engineeringproductivity.platform.requirement.analysis.application.gateway.GroqLlmGateway;
import com.engineeringproductivity.platform.requirement.analysis.application.gateway.LlmGateway;
import com.engineeringproductivity.platform.requirement.analysis.application.gateway.OllamaLlmGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(AnalyzerProperties.class)
public class AnalyzerConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AnalyzerConfiguration.class);

    /**
     * Registers the LLM gateway as a Spring bean so it can be injected wherever needed
     * (e.g. AiPrReviewService uses Optional<LlmGateway>).
     * Returns null for rule-based provider — Spring will treat it as absent bean.
     */
    @Bean
    @Nullable
    public LlmGateway llmGateway(AnalyzerProperties props) {
        return switch (props.provider().toLowerCase()) {
            case "gemini" -> {
                assertApiKey("gemini", props.gemini() != null ? props.gemini().apiKey() : null);
                yield new GeminiLlmGateway(props.gemini(), RestClient.builder().build());
            }
            case "groq" -> {
                assertApiKey("groq", props.groq() != null ? props.groq().apiKey() : null);
                yield new GroqLlmGateway(props.groq(), RestClient.builder().build());
            }
            case "ollama" -> {
                var ollama = props.ollama() != null ? props.ollama()
                        : new AnalyzerProperties.OllamaProperties(null, null, 0);
                yield new OllamaLlmGateway(ollama, RestClient.builder().build());
            }
            default -> null;
        };
    }

    @Bean
    public RequirementAnalyzer requirementAnalyzer(
            AnalyzerProperties props,
            PromptBuilder promptBuilder,
            @Nullable LlmGateway llmGateway
    ) {
        String provider = props.provider();
        log.info("Configuring requirement analyzer: provider={}, fallback={}", provider, props.fallbackToRuleBased());

        RequirementAnalyzer primary;
        if (llmGateway != null && !provider.equalsIgnoreCase("rule-based")) {
            primary = new LlmRequirementAnalyzer(llmGateway, promptBuilder);
        } else {
            log.info("Using rule-based analyzer (no LLM)");
            primary = new RuleBasedRequirementAnalyzer();
        }

        if (llmGateway != null && props.fallbackToRuleBased()) {
            log.info("LLM fallback to rule-based is enabled");
            return new FallbackRequirementAnalyzer(primary, new RuleBasedRequirementAnalyzer());
        }

        return primary;
    }

    private void assertApiKey(String provider, String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "analyzer.%s.api-key must be set when provider=%s".formatted(provider, provider));
        }
    }
}
