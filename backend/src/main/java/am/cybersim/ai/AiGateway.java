package am.cybersim.ai;

import am.cybersim.ai.AiProvider.AiRequest;
import am.cybersim.ai.AiProvider.AiResponse;
import am.cybersim.ai.dto.AiDtos.AiSource;
import am.cybersim.config.AppProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * Single entry point for every AI call — implements the reliability rules of requirement §9.
 *
 * <pre>
 * payload ─▶ AiPromptBuilder ─▶ primary provider (hard timeout) ─▶ AiOutputValidator ─▶ result (source AI)
 *                                     │ exception / timeout / refusal / invalid output
 *                                     └──────────▶ MockAiProvider ─▶ validator ─▶ result (source FALLBACK)
 * every call ─▶ ai_interactions (type, provider, status, latency, tokens)
 * </pre>
 *
 * <p>Inputs: a typed {@link AiPayload}, the audit context and a validator/parser for the expected output.
 * Output: the validated value plus its source. This method never throws because of the AI provider — a broken or
 * unreachable model can never break an authoring session.
 */
@Component
public class AiGateway {

    private static final Logger log = LoggerFactory.getLogger(AiGateway.class);
    private static final int MAX_LOGGED_CHARS = 10_000;

    private final AiProvider primary;
    private final MockAiProvider mock;
    private final AiPromptBuilder promptBuilder;
    private final AiInteractionRepository interactions;
    private final AppProperties properties;
    private final Clock clock;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public AiGateway(AiProvider primary, MockAiProvider mock, AiPromptBuilder promptBuilder,
                     AiInteractionRepository interactions, AppProperties properties, Clock clock) {
        this.primary = primary;
        this.mock = mock;
        this.promptBuilder = promptBuilder;
        this.interactions = interactions;
        this.properties = properties;
        this.clock = clock;
    }

    public record CallContext(Long userId, Long scenarioId, String requestText) {
    }

    public record AiResult<T>(T value, AiSource source, Long interactionId) {
    }

    public <T> AiResult<T> execute(AiPayload payload, CallContext context, Function<String, T> validator) {
        AiRequest request = promptBuilder.build(payload);
        if (primary.type() == AiProvider.Type.MOCK) {
            return runMock(request, context, validator, AiInteraction.Status.SUCCESS, AiSource.MOCK);
        }
        long start = System.nanoTime();
        try {
            AiResponse response = callWithTimeout(request);
            T value = validator.apply(response.text());
            Long id = record(context, request.task(), primary.type(), response, AiInteraction.Status.SUCCESS, start);
            return new AiResult<>(value, AiSource.AI, id);
        } catch (RuntimeException | TimeoutException | ExecutionException e) {
            log.warn("AI {} via {} failed after {} ms ({}): using fallback", request.task(), primary.type(),
                    elapsedMs(start), rootMessage(e));
            return runMock(request, context, validator, AiInteraction.Status.FALLBACK, AiSource.FALLBACK);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return runMock(request, context, validator, AiInteraction.Status.FALLBACK, AiSource.FALLBACK);
        }
    }

    private AiResponse callWithTimeout(AiRequest request)
            throws InterruptedException, ExecutionException, TimeoutException {
        Future<AiResponse> future = executor.submit(() -> primary.complete(request));
        try {
            // hard upper bound: the SDK timeout applies per attempt and the client retries once
            return future.get(properties.ai().timeoutSeconds() * 2L + 1, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw e;
        }
    }

    private <T> AiResult<T> runMock(AiRequest request, CallContext context, Function<String, T> validator,
                                    AiInteraction.Status status, AiSource source) {
        long start = System.nanoTime();
        AiResponse response = mock.complete(request);
        T value = validator.apply(response.text());
        Long id = record(context, request.task(), AiProvider.Type.MOCK, response, status, start);
        return new AiResult<>(value, source, id);
    }

    private Long record(CallContext ctx, AiTask task, AiProvider.Type provider, AiResponse response,
                        AiInteraction.Status status, long start) {
        AiInteraction interaction = new AiInteraction(ctx.userId(), ctx.scenarioId(), task,
                provider, response.model(), status, truncate(ctx.requestText()), truncate(response.text()),
                (int) elapsedMs(start), response.inputTokens(), response.outputTokens(), clock.instant());
        interactions.save(interaction);
        log.info("AI {} provider={} status={} latency={}ms", task, provider, status, interaction.getLatencyMs());
        return interaction.getId();
    }

    private static long elapsedMs(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }

    private static String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= MAX_LOGGED_CHARS ? text : text.substring(0, MAX_LOGGED_CHARS) + "…";
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t.getClass().getSimpleName() + (t.getMessage() != null ? ": " + t.getMessage() : "");
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
