package am.cybersim.simulation.dto;

import am.cybersim.ai.dto.AiFeedback;
import am.cybersim.scenario.ScenarioEnums.ActionCategory;
import am.cybersim.scenario.ScenarioEnums.ActionOutcome;
import am.cybersim.scenario.ScenarioEnums.ActionPhase;
import am.cybersim.scenario.ScenarioEnums.Category;
import am.cybersim.scenario.ScenarioEnums.Difficulty;
import am.cybersim.scenario.ScenarioEnums.EventType;
import am.cybersim.scenario.ScenarioEnums.ResourceType;
import am.cybersim.scenario.ScenarioEnums.Severity;
import am.cybersim.scoring.ScoreResult;
import am.cybersim.simulation.SimulationAction;
import am.cybersim.simulation.SimulationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * API models of the simulation module.
 *
 * <p>Information hiding: while a simulation is in progress the student DTOs never contain the outcome of actions,
 * points, or which events are evidence — otherwise the "investigation" would be trivial. These fields are
 * {@code null} until the simulation is completed.
 */
public final class SimulationDtos {

    private SimulationDtos() {
    }

    // ------------------------------------------------------------------ requests

    public record CreateSimulationRequest(@NotNull @Positive Long scenarioId) {
    }

    public record PerformActionRequest(@NotBlank @Size(max = 64) String actionKey, @Size(max = 500) String note) {
    }

    public record FlagEventRequest(boolean flagged) {
    }

    // ------------------------------------------------------------------ responses

    public record SimulationSummary(Long id, Long scenarioId, String scenarioTitle, Difficulty difficulty,
                                    Category category, SimulationStatus status, Integer scorePercent,
                                    int actionCount, int hintsUsed, Instant createdAt, Instant startedAt,
                                    Instant completedAt) {
    }

    public record ScenarioInfo(Long id, String slug, String title, String summary, String description,
                               Difficulty difficulty, Category category, int estimatedMinutes,
                               List<String> learningObjectives) {
    }

    public record ResourceView(String key, ResourceType type, String name, String region, String status,
                               Map<String, Object> properties) {
    }

    public record EventView(Long id, String eventKey, Instant occurredAt, EventType type, String source,
                            Severity severity, String resourceKey, String message, Map<String, Object> details,
                            boolean flagged, Boolean evidence) {
    }

    public record ActionOption(String key, String label, String description, ActionPhase phase,
                               ActionCategory category, String targetResourceKey, boolean performed) {
    }

    public record PerformedActionView(int sequence, String actionKey, String label, ActionPhase phase,
                                      ActionCategory category, String targetResourceKey,
                                      SimulationAction.Result result, String resultMessage, Instant performedAt,
                                      ActionOutcome outcome, Integer points, Boolean outOfOrder) {
    }

    public record SimulationDetail(Long id, SimulationStatus status, ScenarioInfo scenario, int hintsUsed,
                                   Instant createdAt, Instant startedAt, Instant incidentStartedAt,
                                   Instant completedAt, List<ResourceView> resources, List<EventView> events,
                                   List<ActionOption> availableActions, List<PerformedActionView> performedActions) {
    }

    public record ActionResult(PerformedActionView action, int revealedEvents, SimulationDetail simulation) {
    }

    public record EvidenceItem(String eventKey, String message, String note, boolean revealed, boolean flagged) {
    }

    public record EvidenceSummary(int total, int found, int falseFlags, List<EvidenceItem> items) {
    }

    public record SimulationResultView(Long simulationId, Long scenarioId, String scenarioTitle,
                                       SimulationStatus status, int scorePercent, int rawScore, int maxScore,
                                       int hintsUsed, int hintPenaltyTotal, List<ScoreResult.ScoreItem> breakdown,
                                       List<ScoreResult.MissedAction> missedActions, EvidenceSummary evidence,
                                       List<PerformedActionView> performedActions, String incidentExplanation,
                                       String recommendedSolution, AiFeedback feedback, String feedbackSource,
                                       Long durationSeconds, Instant completedAt) {
    }
}
