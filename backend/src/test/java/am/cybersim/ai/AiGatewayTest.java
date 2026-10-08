package am.cybersim.ai;

import am.cybersim.ai.AiGateway.AiResult;
import am.cybersim.ai.dto.AiDtos.AiSource;
import am.cybersim.config.AppProperties;
import am.cybersim.scenario.dto.ScenarioDefinition;
import am.cybersim.support.TestDefinitions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reliability tests of the AI gateway (requirement 9): any provider failure, timeout, or invalid output must
 * result in a validated fallback answer - never in an exception for the administrator.
 */
class AiGatewayTest {

    static final Instant NOW = Instant.parse("2026-10-06T10:00:00Z");

    ObjectMapper objectMapper = JsonMapper.builder().build();
    MockAiProvider mockProvider = new MockAiProvider(objectMapper);
    AiPromptBuilder promptBuilder = new AiPromptBuilder(objectMapper);
    AiOutputValidator validator = new AiOutputValidator(objectMapper);
    AiInteractionRepository repository = mock(AiInteractionRepository.class);
    AiProvider claude = mock(AiProvider.class);
    ScenarioDefinition draft = TestDefinitions.small();

    @BeforeEach
    void setUp() {
        when(claude.type()).thenReturn(AiProvider.Type.CLAUDE);
    }

    AiGateway gateway(AiProvider primary, int timeoutSeconds) {
        var ai = new AppProperties.Ai("claude", "test-key", "claude-opus-5-5", "low", timeoutSeconds, 1024, false);
        var props = new AppProperties(null, null, null, ai, null);
        return new AiGateway(primary, mockProvider, promptBuilder, repository, props, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    AiGateway.CallContext ctx() {
        return new AiGateway.CallContext(1L, 2L, "generate");
    }

    AiPayload generation() {
        return new AiPayload.Generation(draft, "make it about a web shop");
    }

    @Test
    void mockAsPrimaryReturnsMockSourceAndKeepsTheDraft() {
        AiResult<ScenarioDefinition> result = gateway(mockProvider, 5).execute(generation(), ctx(),
                validator::parseScenarioDefinition);
        assertThat(result.source()).isEqualTo(AiSource.MOCK);
        assertThat(result.value()).isEqualTo(draft);
        assertStatusLogged(AiInteraction.Status.SUCCESS, AiProvider.Type.MOCK);
    }

    @Test
    void validRealAnswerIsUsed() {
        when(claude.complete(any())).thenReturn(new AiProvider.AiResponse(objectMapper.writeValueAsString(draft),
                "claude-opus-5-5", 900, 20));
        AiResult<ScenarioDefinition> result = gateway(claude, 5).execute(generation(), ctx(),
                validator::parseScenarioDefinition);
        assertThat(result.source()).isEqualTo(AiSource.AI);
        assertStatusLogged(AiInteraction.Status.SUCCESS, AiProvider.Type.CLAUDE);
    }

    @Test
    void providerExceptionFallsBackToMock() {
        when(claude.complete(any())).thenThrow(new RuntimeException("connection refused"));
        AiResult<ScenarioDefinition> result = gateway(claude, 5).execute(generation(), ctx(),
                validator::parseScenarioDefinition);
        assertThat(result.source()).isEqualTo(AiSource.FALLBACK);
        assertThat(result.value()).isEqualTo(draft);
        assertStatusLogged(AiInteraction.Status.FALLBACK, AiProvider.Type.MOCK);
    }

    @Test
    void invalidJsonFallsBackToMock() {
        when(claude.complete(any())).thenReturn(new AiProvider.AiResponse("Sure! Here is your scenario", "m", 1, 1));
        AiResult<ScenarioDefinition> result = gateway(claude, 5).execute(generation(), ctx(),
                validator::parseScenarioDefinition);
        assertThat(result.source()).isEqualTo(AiSource.FALLBACK);
        assertThat(result.value()).isEqualTo(draft);
    }

    @Test
    void slowProviderTimesOutAndFallsBack() {
        when(claude.complete(any())).thenAnswer(inv -> {
            Thread.sleep(10_000);
            return new AiProvider.AiResponse("late", "m", 1, 1);
        });
        long start = System.nanoTime();
        AiResult<ScenarioDefinition> result = gateway(claude, 1).execute(generation(), ctx(),
                validator::parseScenarioDefinition);
        assertThat(result.source()).isEqualTo(AiSource.FALLBACK);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(6));
    }

    private void assertStatusLogged(AiInteraction.Status status, AiProvider.Type provider) {
        ArgumentCaptor<AiInteraction> captor = ArgumentCaptor.forClass(AiInteraction.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(status);
        assertThat(captor.getValue().getProvider()).isEqualTo(provider);
        assertThat(captor.getValue().getUserId()).isEqualTo(1L);
        assertThat(captor.getValue().getInteractionType()).isEqualTo(AiTask.GENERATION);
    }
}
