package am.cybersim.scenario;

import am.cybersim.scenario.dto.ScenarioDefinition;
import am.cybersim.scenario.dto.ScenarioDefinition.ActionDef;
import am.cybersim.scenario.dto.ScenarioDefinition.EventDef;
import am.cybersim.scenario.dto.ScenarioDefinition.ResourceDef;
import am.cybersim.scenario.dto.ScenarioDtos.AdminScenarioDetail;
import am.cybersim.scenario.dto.ScenarioDtos.AdminScenarioSummary;
import am.cybersim.scenario.dto.ScenarioDtos.ScenarioBriefing;
import am.cybersim.scenario.dto.ScenarioDtos.ScenarioSummary;
import org.springframework.stereotype.Component;

import java.util.List;

/** Converts between scenario entities and DTOs, in both directions. */
@Component
public class ScenarioMapper {

    public ScenarioSummary toSummary(Scenario s) {
        return new ScenarioSummary(s.getId(), s.getSlug(), s.getTitle(), s.getSummary(), s.getDifficulty(),
                s.getCategory(), s.getEstimatedMinutes());
    }

    public ScenarioBriefing toBriefing(Scenario s) {
        return new ScenarioBriefing(s.getId(), s.getSlug(), s.getTitle(), s.getSummary(), s.getDescription(),
                s.getDifficulty(), s.getCategory(), s.getEstimatedMinutes(),
                s.getObjectives().stream().map(ScenarioObjective::getText).toList(),
                s.getResources().size());
    }

    public AdminScenarioSummary toAdminSummary(Scenario s, long attemptCount) {
        return new AdminScenarioSummary(s.getId(), s.getSlug(), s.getTitle(), s.getDifficulty(), s.getCategory(),
                s.isActive(), s.getVersion(), s.getSourceScenarioId(), s.getActions().size(), s.getEvents().size(),
                s.maxScore(), attemptCount, s.getUpdatedAt());
    }

    public AdminScenarioDetail toAdminDetail(Scenario s) {
        return new AdminScenarioDetail(s.getId(), s.getVersion(), s.isActive(), s.getSourceScenarioId(),
                s.getCreatedAt(), s.getUpdatedAt(), s.maxScore(), toDefinition(s));
    }

    public ScenarioDefinition toDefinition(Scenario s) {
        List<ResourceDef> resources = s.getResources().stream()
                .map(r -> new ResourceDef(r.getResourceKey(), r.getResourceType(), r.getName(), r.getRegion(),
                        r.getStatus(), r.getProperties()))
                .toList();
        List<EventDef> events = s.getEvents().stream()
                .map(e -> new EventDef(e.getEventKey(), e.getOffsetSeconds(), e.getEventType(), e.getSource(),
                        e.getSeverity(), e.getResourceKey(), e.getMessage(), e.getDetails(), e.isEvidence(),
                        e.getEvidenceNote(), e.getRevealedByActionKey()))
                .toList();
        List<ActionDef> actions = s.getActions().stream()
                .map(a -> new ActionDef(a.getActionKey(), a.getLabel(), a.getDescription(), a.getPhase(),
                        a.getCategory(), a.getTargetResourceKey(), a.getOutcome(), a.getPoints(),
                        a.getPrerequisiteActionKey(), a.getEffectStatus(), a.getResultMessage(), a.getExplanation()))
                .toList();
        return new ScenarioDefinition(s.getSlug(), s.getTitle(), s.getSummary(), s.getDescription(),
                s.getDifficulty(), s.getCategory(), s.getEstimatedMinutes(), s.getIncidentExplanation(),
                s.getRecommendedSolution(), s.getHintPenalty(), s.getOutOfOrderPenalty(), s.isActive(),
                s.getObjectives().stream().map(ScenarioObjective::getText).toList(),
                resources, events, actions,
                s.getHints().stream().map(ScenarioHint::getText).toList());
    }

    /**
     * Copies a (validated) definition into the entity. Children must already have been cleared and flushed.
     * The slug is not changed here — it is the stable identity of the scenario.
     */
    public void applyDefinition(Scenario s, ScenarioDefinition d) {
        s.updateDetails(d.title(), d.summary(), d.description(), d.difficulty(), d.category(),
                d.estimatedMinutes(), d.incidentExplanation(), d.recommendedSolution(), d.hintPenalty(),
                d.outOfOrderPenalty());
        for (int i = 0; i < d.learningObjectives().size(); i++) {
            s.addObjective(new ScenarioObjective(i, d.learningObjectives().get(i)));
        }
        for (int i = 0; i < d.resources().size(); i++) {
            ResourceDef r = d.resources().get(i);
            s.addResource(new ScenarioResource(i, r.key(), r.type(), r.name(), r.region(), r.status(), r.properties()));
        }
        for (int i = 0; i < d.events().size(); i++) {
            EventDef e = d.events().get(i);
            s.addEvent(new ScenarioEvent(i, e.key(), e.offsetSeconds(), e.type(), e.source(), e.severity(),
                    e.resourceKey(), e.message(), e.details(), e.evidence(), blankToNull(e.evidenceNote()),
                    blankToNull(e.revealedByActionKey())));
        }
        for (int i = 0; i < d.actions().size(); i++) {
            ActionDef a = d.actions().get(i);
            s.addAction(new ScenarioAction(i, a.key(), a.label(), a.description(), a.phase(), a.category(),
                    blankToNull(a.targetResourceKey()), a.outcome(), a.points(),
                    blankToNull(a.prerequisiteActionKey()), blankToNull(a.effectStatus()),
                    a.resultMessage(), a.explanation()));
        }
        for (int i = 0; i < d.hints().size(); i++) {
            s.addHint(new ScenarioHint(i, d.hints().get(i)));
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
