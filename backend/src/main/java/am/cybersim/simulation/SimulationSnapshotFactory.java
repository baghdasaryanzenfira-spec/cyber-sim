package am.cybersim.simulation;

import am.cybersim.ai.context.SimulationSnapshot;
import am.cybersim.ai.context.SimulationSnapshot.CatalogueAction;
import am.cybersim.ai.context.SimulationSnapshot.EventView;
import am.cybersim.ai.context.SimulationSnapshot.EvidenceView;
import am.cybersim.ai.context.SimulationSnapshot.PerformedView;
import am.cybersim.ai.context.SimulationSnapshot.ResourceView;
import am.cybersim.ai.context.SimulationSnapshot.ScenarioView;
import am.cybersim.scenario.Scenario;
import am.cybersim.scenario.ScenarioHint;
import am.cybersim.scenario.ScenarioObjective;
import org.springframework.stereotype.Component;

/** Converts a simulation aggregate into the immutable {@link SimulationSnapshot} used by the AI module. */
@Component
public class SimulationSnapshotFactory {

    private final SimulationMapper mapper;

    public SimulationSnapshotFactory(SimulationMapper mapper) {
        this.mapper = mapper;
    }

    /** Must be called inside a transaction (lazy collections are read). */
    public SimulationSnapshot create(Simulation s) {
        Scenario sc = s.getScenario();
        ScenarioView scenario = new ScenarioView(sc.getId(), sc.getTitle(), sc.getSummary(), sc.getDescription(),
                sc.getCategory(), sc.getDifficulty(),
                sc.getObjectives().stream().map(ScenarioObjective::getText).toList(),
                sc.getHints().stream().map(ScenarioHint::getText).toList(),
                sc.getIncidentExplanation(), sc.getRecommendedSolution());
        return new SimulationSnapshot(
                s.getId(), s.getUser().getId(), scenario, s.getStatus().name(), s.getHintsUsed(),
                s.getResources().stream().map(r -> new ResourceView(r.getResourceKey(), r.getResourceType().name(),
                        r.getName(), r.getStatus())).toList(),
                s.getEvents().stream().sorted(SimulationMapper.TIMELINE).map(e -> new EventView(e.getOccurredAt(), e.getEventType().name(),
                        e.getSource(), e.getSeverity().name(), e.getResourceKey(), e.getMessage(), e.isFlagged()))
                        .toList(),
                s.getActions().stream().map(a -> new PerformedView(a.getSequence(), a.getActionKey(), a.getLabel(),
                        a.getPhase(), a.getCategory(), a.getOutcome(), a.isOutOfOrder(), a.isDuplicate(),
                        a.getPointsAwarded())).toList(),
                sc.getActions().stream().map(a -> new CatalogueAction(a.getActionKey(), a.getLabel(), a.getPhase(),
                        a.getCategory(), a.getOutcome(), a.getPoints(), a.getPrerequisiteActionKey(),
                        a.getExplanation())).toList(),
                mapper.evidenceSummary(s).items().stream().map(i -> new EvidenceView(i.eventKey(), i.message(),
                        i.note(), i.revealed(), i.flagged())).toList());
    }
}
