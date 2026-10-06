package am.cybersim.ai;

/**
 * Abstraction over an LLM provider (ADR-8). Implementations: {@link ClaudeAiProvider} (Claude API) and
 * {@link MockAiProvider} (deterministic, offline).
 *
 * <p>Contract: return the raw model output or throw. Implementations must not interpret, validate or
 * persist anything — that is done by {@link AiGateway}, so every provider is treated as untrusted in the same way.
 */
public interface AiProvider {

    Type type();

    AiResponse complete(AiRequest request);

    enum Type { CLAUDE, MOCK }

    /**
     * @param outputSchema when not null, the provider should return JSON matching this Java type
     *                     (Claude: structured outputs; mock: serialised object)
     */
    record AiRequest(AiPayload payload, String systemPrompt, String userPrompt, Class<?> outputSchema) {

        public AiTask task() {
            return payload.task();
        }
    }

    record AiResponse(String text, String model, Integer inputTokens, Integer outputTokens) {
    }
}
