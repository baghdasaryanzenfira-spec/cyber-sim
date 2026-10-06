package am.cybersim.ai;

import am.cybersim.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import tools.jackson.databind.ObjectMapper;

import java.util.Locale;

/**
 * Selects the primary AI provider from configuration.
 * {@code AI_PROVIDER=claude} with a non-empty {@code ANTHROPIC_API_KEY} → Claude; anything else → mock.
 * The application therefore always starts, even without an API key.
 */
@Configuration
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    @Bean
    @Primary
    AiProvider primaryAiProvider(AppProperties properties, MockAiProvider mock, ObjectMapper objectMapper) {
        AppProperties.Ai ai = properties.ai();
        String provider = ai.provider() == null ? "mock" : ai.provider().toLowerCase(Locale.ROOT);
        if ("claude".equals(provider)) {
            if (ai.anthropicApiKey() == null || ai.anthropicApiKey().isBlank()) {
                log.warn("AI_PROVIDER=claude but ANTHROPIC_API_KEY is empty - using the mock AI provider");
                return mock;
            }
            log.info("AI provider: Claude API (model {}, effort {}, timeout {}s)", ai.model(), ai.effort(),
                    ai.timeoutSeconds());
            return new ClaudeAiProvider(ai, objectMapper);
        }
        log.info("AI provider: mock (deterministic, offline)");
        return mock;
    }
}
