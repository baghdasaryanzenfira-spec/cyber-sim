package am.cybersim.simulation;

import am.cybersim.common.ApiException;
import am.cybersim.scenario.Scenario;
import am.cybersim.scenario.ScenarioAction;
import am.cybersim.scenario.ScenarioEvent;
import am.cybersim.scoring.ScoringEngine;
import am.cybersim.user.User;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generic, data-driven simulation engine (ADR-5): it contains no scenario-specific logic.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>{@link #start}: copy the scenario's initial infrastructure, place the incident on a realistic time line
 *       and make the initially visible logs/alerts available;</li>
 *   <li>{@link #apply}: validate and apply one student action — state transition, effect on the target resource,
 *       progressive disclosure of new log entries, analyst event on the timeline, points snapshot.</li>
 * </ul>
 *
 * <p>Assumptions: the scenario has passed {@code ScenarioDefinitionValidator}, so all referenced keys exist.
 * Persistence and ownership checks are done by {@link SimulationService}; this class only mutates the aggregate,
 * which makes it unit-testable without a database.
 *
 * <p>Safety: actions only change rows of this simulation — nothing is executed against any real system.
 */
@Component
public class SimulationEngine {

    /** The incident is placed so that its last scenario event happened this long before the student starts. */
    static final Duration PAGING_DELAY = Duration.ofSeconds(60);

    private final ScoringEngine scoringEngine;

    public SimulationEngine(ScoringEngine scoringEngine) {
        this.scoringEngine = scoringEngine;
    }

    public void start(Simulation simulation, Instant now) {
        Scenario scenario = simulation.getScenario();
        int lastOffset = scenario.getEvents().stream().mapToInt(ScenarioEvent::getOffsetSeconds).max().orElse(0);
        Instant incidentStart = now.minusSeconds(lastOffset).minus(PAGING_DELAY);

        simulation.markStarted(now, incidentStart);
        scenario.getResources().forEach(r -> simulation.addResource(SimulationResource.copyOf(r)));
        scenario.getEvents().stream()
                .filter(ScenarioEvent::isInitiallyVisible)
                .forEach(e -> simulation.addEvent(SimulationEvent.materialize(e, incidentStart, now)));
    }

    public AppliedAction apply(Simulation simulation, User actor, String actionKey, String note, Instant now) {
        SimulationStateMachine.requireInProgress(simulation.getStatus(), "perform actions on");
        Scenario scenario = simulation.getScenario();
        ScenarioAction definition = scenario.findAction(actionKey)
                .orElseThrow(() -> ApiException.badRequest("UNKNOWN_ACTION", "Unknown action '" + actionKey + "'"));

        boolean duplicate = simulation.hasApplied(actionKey);
        boolean outOfOrder = !duplicate
                && definition.getPrerequisiteActionKey() != null
                && !simulation.hasApplied(definition.getPrerequisiteActionKey());
        int points = scoringEngine.pointsFor(definition, outOfOrder, duplicate, scenario.getOutOfOrderPenalty());

        Map<String, Object> metadata = new LinkedHashMap<>();
        if (note != null && !note.isBlank()) {
            metadata.put("note", note.strip());
        }
        metadata.put("statusBefore", simulation.getStatus().name());

        int revealed = 0;
        String resultMessage;
        if (duplicate) {
            resultMessage = "This action was already performed; nothing changed.";
        } else {
            simulation.changeStatus(SimulationStateMachine.afterAction(simulation.getStatus(), definition.getPhase()));
            applyEffect(simulation, definition, metadata);
            revealed = revealEvents(simulation, scenario, actionKey, now);
            simulation.addEvent(SimulationEvent.analystAction(
                    "Analyst action: " + definition.getLabel(), definition.getTargetResourceKey(), actionKey, now));
            resultMessage = definition.getResultMessage();
        }
        metadata.put("revealedEvents", revealed);

        SimulationAction action = new SimulationAction(actor, simulation.getActions().size() + 1, definition,
                duplicate ? SimulationAction.Result.DUPLICATE : SimulationAction.Result.APPLIED,
                points, outOfOrder, resultMessage, metadata, now);
        simulation.addAction(action);
        return new AppliedAction(action, revealed);
    }

    public void setEventFlag(Simulation simulation, Long eventId, boolean flagged) {
        SimulationStateMachine.requireInProgress(simulation.getStatus(), "flag evidence in");
        SimulationEvent event = simulation.findEvent(eventId)
                .orElseThrow(() -> ApiException.notFound("Event"));
        if (event.getEventType() == am.cybersim.scenario.ScenarioEnums.EventType.SYSTEM) {
            throw ApiException.badRequest("NOT_FLAGGABLE", "Analyst actions cannot be flagged as evidence");
        }
        event.setFlagged(flagged);
    }

    private static void applyEffect(Simulation simulation, ScenarioAction definition, Map<String, Object> metadata) {
        if (definition.getEffectStatus() == null || definition.getTargetResourceKey() == null) {
            return;
        }
        simulation.findResource(definition.getTargetResourceKey()).ifPresent(resource -> {
            metadata.put("resourceStatusBefore", resource.getStatus());
            resource.changeStatus(definition.getEffectStatus());
            metadata.put("resourceStatusAfter", definition.getEffectStatus());
        });
    }

    private static int revealEvents(Simulation simulation, Scenario scenario, String actionKey, Instant now) {
        List<ScenarioEvent> toReveal = scenario.getEvents().stream()
                .filter(e -> actionKey.equals(e.getRevealedByActionKey()))
                .filter(e -> !simulation.hasEvent(e.getEventKey()))
                .toList();
        toReveal.forEach(e -> simulation.addEvent(SimulationEvent.materialize(e, simulation.getIncidentStartedAt(), now)));
        return toReveal.size();
    }

    public record AppliedAction(SimulationAction action, int revealedEvents) {
    }
}
