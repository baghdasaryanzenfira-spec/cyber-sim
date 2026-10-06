package am.cybersim.ai;

import am.cybersim.ai.AiGateway.AiResult;
import am.cybersim.ai.context.SimulationSnapshot;
import am.cybersim.ai.dto.AiDtos.AiSource;
import am.cybersim.ai.dto.AiFeedback;
import am.cybersim.config.AppProperties;
import am.cybersim.scoring.ScoringEngine;
import am.cybersim.simulation.Simulation;
import am.cybersim.simulation.SimulationEngine;
import am.cybersim.simulation.SimulationMapper;
import am.cybersim.simulation.SimulationSnapshotFactory;
import am.cybersim.support.TestScenarios;
import am.cybersim.user.Role;
import am.cybersim.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reliability tests of the AI gateway (requirement §9): any provider failure, timeout, or invalid output must
 * result in a validated fallback answer — never in an exception for the student.
 */
class AiGatewayTest {

    static final Instant NOW = Instant.parse("2026-10-06T10:00:00Z");

    ObjectMapper objectMapper = JsonMapper.builder().build();
    MockAiProvider mockProvider = new MockAiProvider(objectMapper);
    AiPromptBuilder promptBuilder = new AiPromptBuilder(objectMapper);
    AiOutputValidator validator = new AiOutputValidator(objectMapper);
    AiInteractionRepository repository = mock(AiInteractionRepository.class);
    AiProvider claude = mock(AiProvider.class);
    SimulationSnapshot snapshot;

    @BeforeEach
    void setUp() {
        when(claude.type()).thenReturn(AiProvider.Type.CLAUDE);
        User student = new User("s@test.local", "Student", "hash", Role.STUDENT, NOW);
        Simulation simulation = new Simulation(student, TestScenarios.scenario(), NOW);
        new SimulationEngine(new ScoringEngine()).start(simulation, NOW);
        snapshot = new SimulationSnapshotFactory(new SimulationMapper()).create(simulation);
    }

    AiGateway gateway(AiProvider primary, int timeoutSeconds) {
        var ai = new AppProperties.Ai("claude", "test-key", "claude-opus-5-5", "low", timeoutSeconds, 1024, false);
        var props = new AppProperties(null, null, null, ai, null);
        return new AiGateway(primary, mockProvider, promptBuilder, repository, props, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    AiGateway.CallContext ctx() {
        return new AiGateway.CallContext(1L, 2L, 3L, "question");
    }

    @Test
    void mockAsPrimaryReturnsMockSource() {
        AiResult<String> result = gateway(mockProvider, 5).execute(new AiPayload.Hint(snapshot, 1), ctx(),
                text -> validator.validateHint(text, snapshot));
        assertThat(result.source()).isEqualTo(AiSource.MOCK);
        assertThat(result.value()).isNotBlank();
        assertStatusLogged(AiInteraction.Status.SUCCESS, AiProvider.Type.MOCK);
    }

    @Test
    void validRealAnswerIsUsed() {
        when(claude.complete(any())).thenReturn(new AiProvider.AiResponse("Look at the authentication log.", "claude-opus-5-5", 900, 20));
        AiResult<String> result = gateway(claude, 5).execute(new AiPayload.Hint(snapshot, 1), ctx(),
                text -> validator.validateHint(text, snapshot));
        assertThat(result.source()).isEqualTo(AiSource.AI);
        assertThat(result.value()).isEqualTo("Look at the authentication log.");
        assertStatusLogged(AiInteraction.Status.SUCCESS, AiProvider.Type.CLAUDE);
    }

    @Test
    void providerExceptionFallsBackToMock() {
        when(claude.complete(any())).thenThrow(new RuntimeException("connection refused"));
        AiResult<String> result = gateway(claude, 5).execute(new AiPayload.Question(snapshot, "What is MFA?"), ctx(),
                validator::validateAnswer);
        assertThat(result.source()).isEqualTo(AiSource.FALLBACK);
        assertThat(result.value()).contains("factor");
        assertStatusLogged(AiInteraction.Status.FALLBACK, AiProvider.Type.MOCK);
    }

    @Test
    void invalidJsonFeedbackFallsBackToMock() {
        when(claude.complete(any())).thenReturn(new AiProvider.AiResponse("Sure! Here is your feedback: great job", "m", 1, 1));
        var score = new ScoringEngine().score(TestScenarios.scenario().getActions(), List.of(), 0, 2);
        AiResult<AiFeedback> result = gateway(claude, 5).execute(new AiPayload.Feedback(snapshot, score), ctx(),
                validator::validateFeedback);
        assertThat(result.source()).isEqualTo(AiSource.FALLBACK);
        assertThat(result.value().summary()).contains("0/100");
        assertThat(result.value().improvements()).isNotEmpty();
    }

    @Test
    void hintThatRevealsTheSolutionIsRejected() {
        when(claude.complete(any())).thenReturn(new AiProvider.AiResponse(
                "Do Label inspect-log, then Label identify-vm, then Label isolate-vm.", "m", 1, 1));
        AiResult<String> result = gateway(claude, 5).execute(new AiPayload.Hint(snapshot, 1), ctx(),
                text -> validator.validateHint(text, snapshot));
        assertThat(result.source()).isEqualTo(AiSource.FALLBACK);
    }

    @Test
    void slowProviderTimesOutAndFallsBack() {
        when(claude.complete(any())).thenAnswer(inv -> {
            Thread.sleep(10_000);
            return new AiProvider.AiResponse("late", "m", 1, 1);
        });
        long start = System.nanoTime();
        AiResult<String> result = gateway(claude, 1).execute(new AiPayload.Hint(snapshot, 1), ctx(),
                text -> validator.validateHint(text, snapshot));
        assertThat(result.source()).isEqualTo(AiSource.FALLBACK);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(6));
    }

    private void assertStatusLogged(AiInteraction.Status status, AiProvider.Type provider) {
        ArgumentCaptor<AiInteraction> captor = ArgumentCaptor.forClass(AiInteraction.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(status);
        assertThat(captor.getValue().getProvider()).isEqualTo(provider);
        assertThat(captor.getValue().getUserId()).isEqualTo(1L);
    }
}
