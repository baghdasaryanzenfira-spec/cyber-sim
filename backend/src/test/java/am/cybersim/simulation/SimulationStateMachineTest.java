package am.cybersim.simulation;

import am.cybersim.common.ApiException;
import am.cybersim.scenario.ScenarioEnums.ActionPhase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static am.cybersim.simulation.SimulationStatus.ABANDONED;
import static am.cybersim.simulation.SimulationStatus.COMPLETED;
import static am.cybersim.simulation.SimulationStatus.CREATED;
import static am.cybersim.simulation.SimulationStatus.INVESTIGATING;
import static am.cybersim.simulation.SimulationStatus.RESPONDING;
import static am.cybersim.simulation.SimulationStatus.RUNNING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimulationStateMachineTest {

    @Test
    void startOnlyFromCreated() {
        assertThat(SimulationStateMachine.start(CREATED)).isEqualTo(RUNNING);
        for (SimulationStatus s : new SimulationStatus[]{RUNNING, INVESTIGATING, RESPONDING, COMPLETED, ABANDONED}) {
            assertConflict(() -> SimulationStateMachine.start(s));
        }
    }

    @Test
    void investigationMovesRunningToInvestigatingButNeverBackwards() {
        assertThat(SimulationStateMachine.afterAction(RUNNING, ActionPhase.INVESTIGATION)).isEqualTo(INVESTIGATING);
        assertThat(SimulationStateMachine.afterAction(INVESTIGATING, ActionPhase.INVESTIGATION)).isEqualTo(INVESTIGATING);
        assertThat(SimulationStateMachine.afterAction(RESPONDING, ActionPhase.INVESTIGATION)).isEqualTo(RESPONDING);
    }

    @Test
    void responseMovesToResponding() {
        assertThat(SimulationStateMachine.afterAction(RUNNING, ActionPhase.RESPONSE)).isEqualTo(RESPONDING);
        assertThat(SimulationStateMachine.afterAction(INVESTIGATING, ActionPhase.RESPONSE)).isEqualTo(RESPONDING);
        assertThat(SimulationStateMachine.afterAction(RESPONDING, ActionPhase.RESPONSE)).isEqualTo(RESPONDING);
    }

    @ParameterizedTest
    @EnumSource(value = SimulationStatus.class, names = {"CREATED", "COMPLETED", "ABANDONED"})
    void noActionsOutsideProgressStates(SimulationStatus status) {
        assertConflict(() -> SimulationStateMachine.afterAction(status, ActionPhase.INVESTIGATION));
        assertConflict(() -> SimulationStateMachine.complete(status));
    }

    @ParameterizedTest
    @EnumSource(value = SimulationStatus.class, names = {"RUNNING", "INVESTIGATING", "RESPONDING"})
    void completeFromAnyProgressState(SimulationStatus status) {
        assertThat(SimulationStateMachine.complete(status)).isEqualTo(COMPLETED);
    }

    @ParameterizedTest
    @EnumSource(value = SimulationStatus.class, names = {"CREATED", "RUNNING", "INVESTIGATING", "RESPONDING"})
    void abandonFromAnyNonTerminalState(SimulationStatus status) {
        assertThat(SimulationStateMachine.abandon(status)).isEqualTo(ABANDONED);
    }

    @ParameterizedTest
    @EnumSource(value = SimulationStatus.class, names = {"COMPLETED", "ABANDONED"})
    void terminalStatesAreFinal(SimulationStatus status) {
        assertConflict(() -> SimulationStateMachine.abandon(status));
    }

    private static void assertConflict(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode())
                .isEqualTo("INVALID_STATE_TRANSITION");
    }
}
