package am.cybersim.ai.context;

import am.cybersim.scenario.ScenarioEnums.ActionCategory;
import am.cybersim.scenario.ScenarioEnums.ActionOutcome;
import am.cybersim.scenario.ScenarioEnums.ActionPhase;
import am.cybersim.scenario.ScenarioEnums.Category;
import am.cybersim.scenario.ScenarioEnums.Difficulty;

import java.time.Instant;
import java.util.List;

/**
 * Immutable, persistence-free view of a simulation that the AI module works with.
 *
 * <p>Why a snapshot: the AI call can take seconds; building a snapshot inside a short DB transaction and calling
 * the AI outside of it means no database connection or row lock is held while waiting for the network.
 * It also decouples the {@code ai} module from the {@code simulation} entities (no package cycle).
 *
 * <p>Security consideration: the snapshot contains the full solution (catalogue outcomes, explanation).
 * {@code AiPromptBuilder} decides per task which parts are sent to the model — hint prompts never include
 * outcomes, points or the recommended solution.
 */
public record SimulationSnapshot(
        Long simulationId,
        Long userId,
        ScenarioView scenario,
        String status,
        int hintsUsed,
        List<ResourceView> resources,
        List<EventView> visibleEvents,
        List<PerformedView> performedActions,
        List<CatalogueAction> catalogue,
        List<EvidenceView> evidence) {

    public record ScenarioView(Long id, String title, String summary, String description, Category category,
                               Difficulty difficulty, List<String> learningObjectives, List<String> hints,
                               String incidentExplanation, String recommendedSolution) {
    }

    public record ResourceView(String key, String type, String name, String status) {
    }

    public record EventView(Instant occurredAt, String type, String source, String severity, String resourceKey,
                            String message, boolean flagged) {
    }

    public record PerformedView(int sequence, String actionKey, String label, ActionPhase phase,
                                ActionCategory category, ActionOutcome outcome, boolean outOfOrder,
                                boolean duplicate, int points) {
    }

    public record CatalogueAction(String key, String label, ActionPhase phase, ActionCategory category,
                                  ActionOutcome outcome, int points, String prerequisiteKey, String explanation) {
    }

    public record EvidenceView(String eventKey, String message, String note, boolean revealed, boolean flagged) {
    }

    public boolean performed(String actionKey) {
        return performedActions.stream().anyMatch(p -> p.actionKey().equals(actionKey) && !p.duplicate());
    }

    /** Expected actions not yet performed, in catalogue order. */
    public List<CatalogueAction> remainingExpected() {
        return catalogue.stream()
                .filter(a -> a.outcome() == ActionOutcome.EXPECTED)
                .filter(a -> !performed(a.key()))
                .toList();
    }
}
