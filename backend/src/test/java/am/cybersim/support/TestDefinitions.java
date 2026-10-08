package am.cybersim.support;

import am.cybersim.scenario.ScenarioEnums.ActionCategory;
import am.cybersim.scenario.ScenarioEnums.ActionOutcome;
import am.cybersim.scenario.ScenarioEnums.ActionPhase;
import am.cybersim.scenario.ScenarioEnums.Category;
import am.cybersim.scenario.ScenarioEnums.Difficulty;
import am.cybersim.scenario.ScenarioEnums.EventType;
import am.cybersim.scenario.ScenarioEnums.ResourceType;
import am.cybersim.scenario.ScenarioEnums.Severity;
import am.cybersim.scenario.dto.ScenarioDefinition;
import am.cybersim.scenario.dto.ScenarioDefinition.ActionDef;
import am.cybersim.scenario.dto.ScenarioDefinition.EventDef;
import am.cybersim.scenario.dto.ScenarioDefinition.ResourceDef;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/** Scenario definitions for tests: a tiny structurally valid one, and the real seed files. */
public final class TestDefinitions {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private TestDefinitions() {
    }

    public static ActionDef action(String key, String label, ActionPhase phase, ActionCategory category,
                                   ActionOutcome outcome, int points, String prerequisite, String effectStatus) {
        return new ActionDef(key, label, "Description of " + label, phase, category, "vm-1", outcome, points,
                prerequisite, effectStatus, "Result of " + label,
                "Explanation why " + label + " is " + outcome + " in this incident.");
    }

    public static List<ActionDef> smallActions() {
        return new ArrayList<>(List.of(
                action("inspect-log", "Inspect log", ActionPhase.INVESTIGATION, ActionCategory.INSPECT,
                        ActionOutcome.EXPECTED, 10, null, null),
                action("identify-vm", "Identify VM", ActionPhase.INVESTIGATION, ActionCategory.IDENTIFY,
                        ActionOutcome.EXPECTED, 20, "inspect-log", "COMPROMISED"),
                action("isolate-vm", "Isolate VM", ActionPhase.RESPONSE, ActionCategory.CONTAIN,
                        ActionOutcome.EXPECTED, 30, "identify-vm", "ISOLATED"),
                action("check-other", "Check other VM", ActionPhase.INVESTIGATION, ActionCategory.INSPECT,
                        ActionOutcome.NEUTRAL, 0, null, null),
                action("delete-vm", "Delete VM", ActionPhase.RESPONSE, ActionCategory.CONTAIN,
                        ActionOutcome.HARMFUL, -10, null, "TERMINATED")));
    }

    public static List<EventDef> smallEvents() {
        return new ArrayList<>(List.of(
                new EventDef("e-alert", 0, EventType.ALERT, "guard", Severity.HIGH, "vm-1", "Brute force detected",
                        Map.of(), false, null, null),
                new EventDef("e-log", 60, EventType.LOG, "auth.log", Severity.HIGH, "vm-1", "Accepted password",
                        Map.of(), true, "Successful login after many failures", "inspect-log")));
    }

    public static ScenarioDefinition small() {
        return with(smallEvents(), smallActions());
    }

    public static ScenarioDefinition with(List<EventDef> events, List<ActionDef> actions) {
        return new ScenarioDefinition("small-scenario", "Small scenario", "Summary", "Description",
                Difficulty.BEGINNER, Category.NETWORK, 10, "Explanation", "Solution", 2, 5,
                List.of("Objective"),
                List.of(new ResourceDef("vm-1", ResourceType.VIRTUAL_MACHINE, "vm", "eu", "RUNNING", Map.of())),
                events, actions, List.of("hint"));
    }

    /** One of the real seed scenarios: 01-ssh-bruteforce-vm, 02-compromised-credentials, 03-public-storage-bucket. */
    public static ScenarioDefinition seed(String name) {
        try (InputStream in = TestDefinitions.class.getResourceAsStream("/scenarios/" + name + ".json")) {
            return MAPPER.readValue(in, ScenarioDefinition.class);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static ScenarioDefinition mapActions(ScenarioDefinition d, UnaryOperator<List<ActionDef>> change) {
        return new ScenarioDefinition(d.slug(), d.title(), d.summary(), d.description(), d.difficulty(),
                d.category(), d.estimatedMinutes(), d.incidentExplanation(), d.recommendedSolution(),
                d.hintPenalty(), d.outOfOrderPenalty(), d.learningObjectives(), d.resources(), d.events(),
                change.apply(new ArrayList<>(d.actions())), d.hints());
    }

    public static ScenarioDefinition mapEvents(ScenarioDefinition d, UnaryOperator<List<EventDef>> change) {
        return new ScenarioDefinition(d.slug(), d.title(), d.summary(), d.description(), d.difficulty(),
                d.category(), d.estimatedMinutes(), d.incidentExplanation(), d.recommendedSolution(),
                d.hintPenalty(), d.outOfOrderPenalty(), d.learningObjectives(), d.resources(),
                change.apply(new ArrayList<>(d.events())), d.actions(), d.hints());
    }

    public static ScenarioDefinition withSlug(ScenarioDefinition d, String slug) {
        return new ScenarioDefinition(slug, d.title(), d.summary(), d.description(), d.difficulty(),
                d.category(), d.estimatedMinutes(), d.incidentExplanation(), d.recommendedSolution(),
                d.hintPenalty(), d.outOfOrderPenalty(), d.learningObjectives(), d.resources(), d.events(),
                d.actions(), d.hints());
    }
}
