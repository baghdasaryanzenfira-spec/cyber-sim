package am.cybersim.simulation;

import am.cybersim.common.ApiException;
import am.cybersim.scenario.ScenarioEnums.ActionPhase;

/**
 * The allowed transitions of {@link SimulationStatus}. Pure functions, no state — easy to unit-test exhaustively.
 *
 * <pre>
 * CREATED ──start──▶ RUNNING ──investigation──▶ INVESTIGATING ──response──▶ RESPONDING
 *                       │└────────────────response───────────────────────────▲
 *                       └──complete──▶ COMPLETED ◀──complete── INVESTIGATING / RESPONDING
 * any non-terminal ──abandon──▶ ABANDONED
 * </pre>
 *
 * States only move forward: investigating again while RESPONDING keeps the RESPONDING state.
 * Invalid transitions raise 409 {@code INVALID_STATE_TRANSITION}.
 */
public final class SimulationStateMachine {

    private SimulationStateMachine() {
    }

    public static SimulationStatus start(SimulationStatus current) {
        if (current != SimulationStatus.CREATED) {
            throw invalid("start", current);
        }
        return SimulationStatus.RUNNING;
    }

    public static SimulationStatus afterAction(SimulationStatus current, ActionPhase phase) {
        if (!current.isInProgress()) {
            throw invalid("perform actions on", current);
        }
        if (phase == ActionPhase.RESPONSE) {
            return SimulationStatus.RESPONDING;
        }
        return current == SimulationStatus.RUNNING ? SimulationStatus.INVESTIGATING : current;
    }

    public static SimulationStatus complete(SimulationStatus current) {
        if (!current.isInProgress()) {
            throw invalid("complete", current);
        }
        return SimulationStatus.COMPLETED;
    }

    public static SimulationStatus abandon(SimulationStatus current) {
        if (current.isTerminal()) {
            throw invalid("abandon", current);
        }
        return SimulationStatus.ABANDONED;
    }

    public static void requireInProgress(SimulationStatus current, String operation) {
        if (!current.isInProgress()) {
            throw invalid(operation, current);
        }
    }

    private static ApiException invalid(String operation, SimulationStatus current) {
        return ApiException.conflict("INVALID_STATE_TRANSITION",
                "Cannot " + operation + " a simulation in status " + current);
    }
}
