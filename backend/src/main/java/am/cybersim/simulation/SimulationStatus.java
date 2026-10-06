package am.cybersim.simulation;

/** Lifecycle of a simulation — see docs/en/08-simulation-engine.md §2. */
public enum SimulationStatus {
    CREATED,
    RUNNING,
    INVESTIGATING,
    RESPONDING,
    COMPLETED,
    ABANDONED;

    /** States in which the student can act (perform actions, ask the assistant, finish). */
    public boolean isInProgress() {
        return this == RUNNING || this == INVESTIGATING || this == RESPONDING;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == ABANDONED;
    }
}
