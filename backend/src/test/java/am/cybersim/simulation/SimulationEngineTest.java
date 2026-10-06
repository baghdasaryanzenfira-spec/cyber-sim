package am.cybersim.simulation;

import am.cybersim.common.ApiException;
import am.cybersim.scenario.Scenario;
import am.cybersim.scoring.ScoringEngine;
import am.cybersim.support.TestScenarios;
import am.cybersim.user.Role;
import am.cybersim.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Unit tests of the engine on an in-memory aggregate — no Spring, no database. */
class SimulationEngineTest {

    static final Instant NOW = Instant.parse("2026-10-06T10:00:00Z");

    SimulationEngine engine = new SimulationEngine(new ScoringEngine());
    User student = new User("s@test.local", "Student", "hash", Role.STUDENT, NOW);
    Scenario scenario;
    Simulation simulation;

    @BeforeEach
    void setUp() {
        scenario = TestScenarios.scenario();
        simulation = new Simulation(student, scenario, NOW);
    }

    @Test
    void startCopiesResourcesAndShowsOnlyInitiallyVisibleEvents() {
        engine.start(simulation, NOW);

        assertThat(simulation.getStatus()).isEqualTo(SimulationStatus.RUNNING);
        assertThat(simulation.getResources()).extracting(SimulationResource::getResourceKey)
                .containsExactly("vm", "vm-other");
        assertThat(simulation.getEvents()).extracting(SimulationEvent::getEventKey)
                .containsExactlyInAnyOrder("alert", "log-noise");
        // last event (offset 100) happened 60 s before the student started
        assertThat(simulation.getIncidentStartedAt()).isEqualTo(NOW.minusSeconds(160));
        SimulationEvent alert = simulation.getEvents().stream().filter(e -> "alert".equals(e.getEventKey())).findFirst().orElseThrow();
        assertThat(alert.getOccurredAt()).isEqualTo(NOW.minusSeconds(60));
    }

    @Test
    void investigationActionRevealsEventsAndChangesState() {
        engine.start(simulation, NOW);

        var applied = engine.apply(simulation, student, "inspect-log", "checking auth.log", NOW.plusSeconds(30));

        assertThat(simulation.getStatus()).isEqualTo(SimulationStatus.INVESTIGATING);
        assertThat(applied.revealedEvents()).isEqualTo(1);
        assertThat(simulation.hasEvent("log-success")).isTrue();
        assertThat(applied.action().getPointsAwarded()).isEqualTo(10);
        assertThat(applied.action().getMetadata()).containsEntry("note", "checking auth.log");
        // analyst event on the timeline
        assertThat(simulation.getEvents()).anyMatch(e -> SimulationEvent.ANALYST_SOURCE.equals(e.getSource()));
    }

    @Test
    void responseActionChangesResourceStatusAndState() {
        engine.start(simulation, NOW);
        engine.apply(simulation, student, "inspect-log", null, NOW);
        engine.apply(simulation, student, "identify-vm", null, NOW);

        var applied = engine.apply(simulation, student, "isolate-vm", null, NOW);

        assertThat(simulation.getStatus()).isEqualTo(SimulationStatus.RESPONDING);
        assertThat(simulation.findResource("vm").orElseThrow().getStatus()).isEqualTo("ISOLATED");
        assertThat(applied.action().getMetadata()).containsEntry("resourceStatusBefore", "COMPROMISED");
        assertThat(applied.action().isOutOfOrder()).isFalse();
    }

    @Test
    void actionBeforePrerequisiteIsOutOfOrderWithPenalty() {
        engine.start(simulation, NOW);

        var applied = engine.apply(simulation, student, "isolate-vm", null, NOW);

        assertThat(applied.action().isOutOfOrder()).isTrue();
        assertThat(applied.action().getPointsAwarded()).isEqualTo(25); // 30 - 5
    }

    @Test
    void repeatedActionIsDuplicateWithoutEffect() {
        engine.start(simulation, NOW);
        engine.apply(simulation, student, "inspect-log", null, NOW);
        int eventsBefore = simulation.getEvents().size();

        var applied = engine.apply(simulation, student, "inspect-log", null, NOW);

        assertThat(applied.action().isDuplicate()).isTrue();
        assertThat(applied.action().getPointsAwarded()).isZero();
        assertThat(simulation.getEvents()).hasSize(eventsBefore);
        assertThat(simulation.getActions()).hasSize(2);
    }

    @Test
    void harmfulActionGetsNegativePoints() {
        engine.start(simulation, NOW);
        var applied = engine.apply(simulation, student, "delete-vm", null, NOW);
        assertThat(applied.action().getPointsAwarded()).isEqualTo(-10);
        assertThat(simulation.findResource("vm").orElseThrow().getStatus()).isEqualTo("TERMINATED");
    }

    @Test
    void unknownActionIsRejected() {
        engine.start(simulation, NOW);
        assertThatThrownBy(() -> engine.apply(simulation, student, "rm-rf", null, NOW))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode()).isEqualTo("UNKNOWN_ACTION");
    }

    @Test
    void actionsBeforeStartAreRejected() {
        assertThatThrownBy(() -> engine.apply(simulation, student, "inspect-log", null, NOW))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode()).isEqualTo("INVALID_STATE_TRANSITION");
    }
}
