package am.cybersim.authoring;

import am.cybersim.common.ApiError;
import am.cybersim.scenario.ScenarioDefinitionValidator;
import am.cybersim.scenario.ScenarioEnums.ActionCategory;
import am.cybersim.scenario.ScenarioEnums.ActionOutcome;
import am.cybersim.scenario.ScenarioEnums.ActionPhase;
import am.cybersim.scenario.ScenarioEnums.EventType;
import am.cybersim.scenario.dto.ScenarioDefinition;
import am.cybersim.scenario.dto.ScenarioDefinition.ActionDef;
import am.cybersim.scenario.dto.ScenarioDefinition.EventDef;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Graph-based scenario validation (authoring step "Validation finds a wrong scenario or confirms it").
 *
 * <p>Layer 1 is the field/cross-reference validation every write already goes through
 * ({@link ScenarioDefinitionValidator}). Layer 2 runs only when layer 1 is clean, because it assumes that all
 * references resolve, and analyses the {@link ScenarioGraph}: can the correct path be completed, is all
 * evidence obtainable on it, is there a dangerous path to test, are phases ordered sensibly.
 *
 * <p>ERROR issues block publishing; WARNING issues lower the quality score; INFO issues describe the graph.
 */
@Component
public class ScenarioAnalyzer {

    public enum Severity { ERROR, WARNING, INFO }

    public record Issue(Severity severity, String code, String path, String message) {
    }

    public record GraphStats(int actions, int events, int resources, int evidenceEvents, int expectedActions,
                             int harmfulActions, int neutralActions, int maxDepth, int categoriesCovered) {
    }

    public record ValidationReport(boolean valid, int errorCount, int warningCount, GraphStats stats,
                                   List<Issue> issues) {
    }

    private final ScenarioDefinitionValidator validator;

    public ScenarioAnalyzer(ScenarioDefinitionValidator validator) {
        this.validator = validator;
    }

    public ValidationReport analyze(ScenarioDefinition def) {
        List<Issue> issues = new ArrayList<>();
        List<ApiError.FieldError> structural = validator.collectErrors(def);
        for (ApiError.FieldError e : structural) {
            issues.add(new Issue(Severity.ERROR, "STRUCTURE", e.field(), e.message()));
        }
        GraphStats stats = null;
        if (structural.isEmpty()) {
            stats = stats(def);
            graphRules(def, stats, issues);
        }
        int errors = (int) issues.stream().filter(i -> i.severity() == Severity.ERROR).count();
        int warnings = (int) issues.stream().filter(i -> i.severity() == Severity.WARNING).count();
        return new ValidationReport(errors == 0, errors, warnings, stats, List.copyOf(issues));
    }

    static GraphStats stats(ScenarioDefinition def) {
        Set<ActionCategory> categories = new HashSet<>();
        def.actions().stream().filter(ScenarioGraph::isExpected).forEach(a -> categories.add(a.category()));
        return new GraphStats(def.actions().size(), def.events().size(), def.resources().size(),
                (int) def.events().stream().filter(EventDef::evidence).count(),
                (int) def.actions().stream().filter(ScenarioGraph::isExpected).count(),
                (int) def.actions().stream().filter(a -> a.outcome() == ActionOutcome.HARMFUL).count(),
                (int) def.actions().stream().filter(a -> a.outcome() == ActionOutcome.NEUTRAL).count(),
                ScenarioGraph.maxDepth(def), categories.size());
    }

    private void graphRules(ScenarioDefinition def, GraphStats stats, List<Issue> issues) {
        Map<String, ActionDef> actions = ScenarioGraph.actionsByKey(def);

        // --- correctness of the dependency graph
        for (int i = 0; i < def.actions().size(); i++) {
            ActionDef a = def.actions().get(i);
            String path = "actions[" + i + "]";
            ActionDef pre = ScenarioGraph.blank(a.prerequisiteActionKey()) ? null : actions.get(a.prerequisiteActionKey());
            if (pre != null && a.outcome() == ActionOutcome.EXPECTED && pre.outcome() != ActionOutcome.EXPECTED) {
                issues.add(error("EXPECTED_DEPENDS_ON_UNEXPECTED", path + ".prerequisiteActionKey",
                        "Correct action '" + a.label() + "' requires '" + pre.label() + "', which is not a correct action"));
            }
            if (pre != null && a.phase() == ActionPhase.INVESTIGATION && pre.phase() == ActionPhase.RESPONSE) {
                issues.add(error("PHASE_INVERSION", path + ".prerequisiteActionKey",
                        "Investigation action '" + a.label() + "' depends on response action '" + pre.label() + "'"));
            }
        }
        for (int i = 0; i < def.events().size(); i++) {
            EventDef e = def.events().get(i);
            if (ScenarioGraph.blank(e.revealedByActionKey())) {
                continue;
            }
            ActionDef revealer = actions.get(e.revealedByActionKey());
            if (revealer != null && revealer.outcome() != ActionOutcome.EXPECTED) {
                issues.add(new Issue(e.evidence() ? Severity.ERROR : Severity.WARNING, "EVENT_ONLY_VIA_UNEXPECTED_ACTION",
                        "events[" + i + "].revealedByActionKey",
                        (e.evidence() ? "Evidence" : "Event") + " '" + e.key() + "' is only revealed by '"
                                + revealer.label() + "', which is not part of the correct path"));
            }
        }
        if (def.actions().stream().noneMatch(a -> ScenarioGraph.isExpected(a) && a.phase() == ActionPhase.RESPONSE)) {
            issues.add(error("NO_RESPONSE_ACTION", "actions", "The correct path has no RESPONSE action (containment/recovery)"));
        }
        if (stats.harmfulActions() == 0) {
            issues.add(error("NO_DANGEROUS_PATH", "actions",
                    "At least one HARMFUL action is required so that the dangerous path can be tested"));
        }

        // --- warnings: weaknesses that do not block publishing
        if (def.actions().stream().noneMatch(a -> ScenarioGraph.isExpected(a) && a.phase() == ActionPhase.INVESTIGATION)) {
            issues.add(warn("NO_INVESTIGATION_ACTION", "actions", "The correct path has no INVESTIGATION action"));
        }
        if (def.actions().stream().noneMatch(a -> ScenarioGraph.isExpected(a) && a.category() == ActionCategory.CONTAIN)) {
            issues.add(warn("NO_CONTAINMENT", "actions", "No correct CONTAIN action - the incident is never stopped"));
        }
        if (def.events().stream().noneMatch(e -> ScenarioGraph.blank(e.revealedByActionKey()) && e.type() == EventType.ALERT)) {
            issues.add(warn("NO_INITIAL_ALERT", "events", "No alert is visible at the start - nothing triggers the investigation"));
        }
        if (stats.evidenceEvents() < 2) {
            issues.add(warn("FEW_EVIDENCE", "events", "Only " + stats.evidenceEvents() + " evidence event(s); at least 2 recommended"));
        }
        if (stats.neutralActions() == 0) {
            issues.add(warn("NO_DISTRACTORS", "actions", "No NEUTRAL (unnecessary) actions - the choice is too obvious"));
        }
        if (def.hints().isEmpty()) {
            issues.add(warn("NO_HINTS", "hints", "No hints defined"));
        }
        int maxScore = def.actions().stream().filter(ScenarioGraph::isExpected).mapToInt(ActionDef::points).sum();
        for (int i = 0; i < def.actions().size(); i++) {
            ActionDef a = def.actions().get(i);
            if (ScenarioGraph.isExpected(a) && maxScore > 0 && a.points() * 2 > maxScore && stats.expectedActions() > 2) {
                issues.add(warn("UNBALANCED_POINTS", "actions[" + i + "].points",
                        "Action '" + a.label() + "' is worth more than half of the total score"));
            }
            if (a.explanation().strip().length() < 20) {
                issues.add(warn("SHORT_EXPLANATION", "actions[" + i + "].explanation",
                        "Explanation of '" + a.label() + "' is too short to teach anything"));
            }
        }
        Set<String> labels = new HashSet<>();
        for (ActionDef a : def.actions()) {
            if (!labels.add(a.label().strip().toLowerCase(Locale.ROOT))) {
                issues.add(warn("DUPLICATE_LABEL", "actions", "Two actions share the label '" + a.label() + "'"));
            }
        }
        Set<String> referenced = new HashSet<>();
        def.events().forEach(e -> add(referenced, e.resourceKey()));
        def.actions().forEach(a -> add(referenced, a.targetResourceKey()));
        def.resources().forEach(r -> {
            if (!referenced.contains(r.key())) {
                issues.add(warn("ORPHAN_RESOURCE", "resources", "Resource '" + r.name() + "' is not referenced by any event or action"));
            }
        });

        issues.add(new Issue(Severity.INFO, "GRAPH_SUMMARY", "graph",
                stats.actions() + " actions, " + stats.events() + " events, " + stats.resources()
                        + " resources; longest prerequisite chain " + stats.maxDepth()));
    }

    private static void add(Set<String> set, String value) {
        if (!ScenarioGraph.blank(value)) {
            set.add(value);
        }
    }

    private static Issue error(String code, String path, String message) {
        return new Issue(Severity.ERROR, code, path, message);
    }

    private static Issue warn(String code, String path, String message) {
        return new Issue(Severity.WARNING, code, path, message);
    }
}
