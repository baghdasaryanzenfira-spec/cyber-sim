package am.cybersim.simulation;

import am.cybersim.scenario.Scenario;
import am.cybersim.scenario.ScenarioEnums.EventType;
import am.cybersim.scenario.ScenarioEvent;
import am.cybersim.scenario.ScenarioObjective;
import am.cybersim.simulation.dto.SimulationDtos.ActionOption;
import am.cybersim.simulation.dto.SimulationDtos.EventView;
import am.cybersim.simulation.dto.SimulationDtos.EvidenceItem;
import am.cybersim.simulation.dto.SimulationDtos.EvidenceSummary;
import am.cybersim.simulation.dto.SimulationDtos.PerformedActionView;
import am.cybersim.simulation.dto.SimulationDtos.ResourceView;
import am.cybersim.simulation.dto.SimulationDtos.ScenarioInfo;
import am.cybersim.simulation.dto.SimulationDtos.SimulationDetail;
import am.cybersim.simulation.dto.SimulationDtos.SimulationResultView;
import am.cybersim.simulation.dto.SimulationDtos.SimulationSummary;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds simulation DTOs. {@code revealSolution} controls whether hidden fields (outcome, points, evidence)
 * are included: true for completed simulations and for administrators.
 */
@Component
public class SimulationMapper {

    /** Timeline order; new events are appended in memory, so the list is sorted explicitly. */
    static final Comparator<SimulationEvent> TIMELINE = Comparator.comparing(SimulationEvent::getOccurredAt)
            .thenComparing(e -> e.getId() == null ? Long.MAX_VALUE : e.getId());

    public SimulationSummary toSummary(Simulation s, Integer scorePercent) {
        Scenario sc = s.getScenario();
        return new SimulationSummary(s.getId(), sc.getId(), sc.getTitle(), sc.getDifficulty(), sc.getCategory(),
                s.getStatus(), scorePercent, s.getActions().size(), s.getHintsUsed(), s.getCreatedAt(),
                s.getStartedAt(), s.getCompletedAt());
    }

    public SimulationDetail toDetail(Simulation s, boolean revealSolution) {
        Scenario sc = s.getScenario();
        boolean reveal = revealSolution || s.getStatus().isTerminal();
        Set<String> performed = s.getActions().stream()
                .filter(a -> !a.isDuplicate())
                .map(SimulationAction::getActionKey)
                .collect(Collectors.toSet());

        List<ActionOption> options = s.getStatus() == SimulationStatus.CREATED ? List.of()
                : sc.getActions().stream()
                .map(a -> new ActionOption(a.getActionKey(), a.getLabel(), a.getDescription(), a.getPhase(),
                        a.getCategory(), a.getTargetResourceKey(), performed.contains(a.getActionKey())))
                .toList();

        return new SimulationDetail(s.getId(), s.getStatus(), toScenarioInfo(sc), s.getHintsUsed(),
                s.getCreatedAt(), s.getStartedAt(), s.getIncidentStartedAt(), s.getCompletedAt(),
                s.getResources().stream().map(r -> new ResourceView(r.getResourceKey(), r.getResourceType(),
                        r.getName(), r.getRegion(), r.getStatus(), r.getProperties())).toList(),
                s.getEvents().stream().sorted(TIMELINE).map(e -> toEventView(e, reveal)).toList(),
                options,
                s.getActions().stream().map(a -> toPerformedView(a, reveal)).toList());
    }

    public ScenarioInfo toScenarioInfo(Scenario sc) {
        return new ScenarioInfo(sc.getId(), sc.getSlug(), sc.getTitle(), sc.getSummary(), sc.getDescription(),
                sc.getDifficulty(), sc.getCategory(), sc.getEstimatedMinutes(),
                sc.getObjectives().stream().map(ScenarioObjective::getText).toList());
    }

    public EventView toEventView(SimulationEvent e, boolean reveal) {
        return new EventView(e.getId(), e.getEventKey(), e.getOccurredAt(), e.getEventType(), e.getSource(),
                e.getSeverity(), e.getResourceKey(), e.getMessage(), e.getDetails(), e.isFlagged(),
                reveal && e.getEventType() != EventType.SYSTEM ? e.isEvidence() : null);
    }

    public PerformedActionView toPerformedView(SimulationAction a, boolean reveal) {
        return new PerformedActionView(a.getSequence(), a.getActionKey(), a.getLabel(), a.getPhase(), a.getCategory(),
                a.getTargetResourceKey(), a.getResult(), a.getResultMessage(), a.getPerformedAt(),
                reveal ? a.getOutcome() : null,
                reveal ? a.getPointsAwarded() : null,
                reveal ? a.isOutOfOrder() : null);
    }

    /** Which evidence the student found (flagged), missed, or never even uncovered. */
    public EvidenceSummary evidenceSummary(Simulation s) {
        Map<String, SimulationEvent> visible = s.getEvents().stream()
                .filter(e -> e.getEventKey() != null)
                .collect(Collectors.toMap(SimulationEvent::getEventKey, e -> e, (a, b) -> a));
        List<EvidenceItem> items = s.getScenario().getEvents().stream()
                .filter(ScenarioEvent::isEvidence)
                .map(e -> {
                    SimulationEvent v = visible.get(e.getEventKey());
                    return new EvidenceItem(e.getEventKey(), e.getMessage(), e.getEvidenceNote(), v != null,
                            v != null && v.isFlagged());
                })
                .toList();
        int found = (int) items.stream().filter(EvidenceItem::flagged).count();
        int falseFlags = (int) s.getEvents().stream().filter(e -> e.isFlagged() && !e.isEvidence()).count();
        return new EvidenceSummary(items.size(), found, falseFlags, items);
    }

    public SimulationResultView toResultView(Simulation s, SimulationResult r) {
        Scenario sc = s.getScenario();
        Long duration = s.getStartedAt() != null && s.getCompletedAt() != null
                ? Duration.between(s.getStartedAt(), s.getCompletedAt()).toSeconds() : null;
        return new SimulationResultView(s.getId(), sc.getId(), sc.getTitle(), s.getStatus(), r.getScorePercent(),
                r.getRawScore(), r.getMaxScore(), s.getHintsUsed(), r.getHintPenaltyTotal(), r.getBreakdown(),
                r.getMissedActions(), evidenceSummary(s),
                s.getActions().stream().map(a -> toPerformedView(a, true)).toList(),
                sc.getIncidentExplanation(), sc.getRecommendedSolution(), r.getFeedback(), r.getFeedbackSource(),
                duration, s.getCompletedAt());
    }
}
