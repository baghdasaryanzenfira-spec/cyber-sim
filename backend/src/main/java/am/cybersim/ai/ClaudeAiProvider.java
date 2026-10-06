package am.cybersim.ai;

import am.cybersim.config.AppProperties;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Claude API provider built on the official Anthropic Java SDK.
 *
 * <ul>
 *   <li>Model, effort, max tokens and timeout come from configuration ({@code AI_MODEL}, {@code AI_EFFORT}, …).</li>
 *   <li>Tasks with an output schema use <b>structured outputs</b>: the SDK derives a JSON schema from the Java
 *       record and the API guarantees schema-conformant JSON. The content is still validated by
 *       {@link AiOutputValidator} — structurally valid output can still be unsuitable.</li>
 *   <li>A {@code refusal} or truncated ({@code max_tokens}) response is reported as a failure, so the
 *       {@link AiGateway} falls back to the mock provider.</li>
 *   <li>Optional server-side refusal fallback ({@code AI_SERVER_SIDE_FALLBACK}) lets the API retry a declined
 *       request on a fallback model before the application-level fallback is needed.</li>
 * </ul>
 *
 * <p>Created by {@link AiConfig} only when {@code AI_PROVIDER=claude} and an API key is present.
 * The API key is never logged.
 */
public class ClaudeAiProvider implements AiProvider {

    private static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";

    private final AnthropicClient client;
    private final AppProperties.Ai config;
    private final ObjectMapper objectMapper;

    public ClaudeAiProvider(AppProperties.Ai config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.client = AnthropicOkHttpClient.builder()
                .apiKey(config.anthropicApiKey())
                .timeout(Duration.ofSeconds(config.timeoutSeconds()))
                .maxRetries(1)
                .build();
    }

    @Override
    public Type type() {
        return Type.CLAUDE;
    }

    @Override
    public AiResponse complete(AiRequest request) {
        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(config.model())
                .maxTokens(config.maxTokens())
                .system(request.systemPrompt())
                .addUserMessage(request.userPrompt())
                .outputConfig(OutputConfig.builder()
                        .effort(OutputConfig.Effort.of(config.effort().toLowerCase(Locale.ROOT)))
                        .build());
        if (request.outputSchema() != null) {
            return completeStructured(builder, request.outputSchema());
        }
        if (config.serverSideFallback()) {
            builder.putAdditionalHeader("anthropic-beta", FALLBACK_BETA)
                    .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
        }
        Message message = client.messages().create(builder.build());
        checkStopReason(message.stopReason());
        String text = message.content().stream()
                .flatMap(block -> block.text().stream())
                .map(textBlock -> textBlock.text())
                .collect(Collectors.joining("\n"))
                .strip();
        return new AiResponse(text, message.model().asString(), (int) message.usage().inputTokens(),
                (int) message.usage().outputTokens());
    }

    private <T> AiResponse completeStructured(MessageCreateParams.Builder builder, Class<T> schema) {
        StructuredMessageCreateParams.Builder<T> structured = builder.outputConfig(schema);
        if (config.serverSideFallback()) {
            structured.putAdditionalHeader("anthropic-beta", FALLBACK_BETA)
                    .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
        }
        StructuredMessage<T> message = client.messages().create(structured.build());
        checkStopReason(message.stopReason());
        T value = message.content().stream()
                .flatMap(block -> block.text().stream())
                .map(textBlock -> textBlock.text())
                .findFirst()
                .orElseThrow(() -> new AiProviderException("Structured response contained no content"));
        return new AiResponse(objectMapper.writeValueAsString(value), message.model().asString(),
                (int) message.usage().inputTokens(), (int) message.usage().outputTokens());
    }

    private static void checkStopReason(Optional<StopReason> stopReason) {
        if (stopReason.isPresent() && StopReason.REFUSAL.equals(stopReason.get())) {
            throw new AiProviderException("Model declined the request (refusal)");
        }
        if (stopReason.isPresent() && StopReason.MAX_TOKENS.equals(stopReason.get())) {
            throw new AiProviderException("Model output was truncated (max_tokens)");
        }
    }
}
