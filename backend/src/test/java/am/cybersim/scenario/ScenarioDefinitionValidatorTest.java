package am.cybersim.scenario;

import am.cybersim.common.ApiException;
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
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Unit tests of the cross-reference rules that protect the engine from unplayable scenarios. */
class ScenarioDefinitionValidatorTest {

    private static ValidatorFactory factory;
    private static ScenarioDefinitionValidator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = new ScenarioDefinitionValidator(factory.getValidator());
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    static ResourceDef vm() {
        return new ResourceDef("vm-1", ResourceType.VIRTUAL_MACHINE, "vm", "eu", "RUNNING", Map.of());
    }

    static EventDef event(String key, boolean evidence, String revealedBy) {
        return new EventDef(key, 0, EventType.LOG, "auth.log", Severity.HIGH, "vm-1", "msg", Map.of(),
                evidence, evidence ? "why" : null, revealedBy);
    }

    static ActionDef action(String key, ActionPhase phase, ActionOutcome outcome, int points, String prerequisite) {
        return new ActionDef(key, "label", "desc", phase, ActionCategory.INSPECT, "vm-1", outcome, points,
                prerequisite, null, "result", "explanation");
    }

    static ScenarioDefinition definition(List<EventDef> events, List<ActionDef> actions) {
        return new ScenarioDefinition("test-scenario", "Title", "Summary", "Description", Difficulty.BEGINNER,
                Category.NETWORK, 10, "Explanation", "Solution", 2, 5, List.of("Objective"),
                List.of(vm()), events, actions, List.of("hint"));
    }

    static ScenarioDefinition valid() {
        return definition(
                List.of(event("e1", true, null), event("e2", false, "inspect")),
                List.of(action("inspect", ActionPhase.INVESTIGATION, ActionOutcome.EXPECTED, 10, null),
                        action("isolate", ActionPhase.RESPONSE, ActionOutcome.EXPECTED, 20, "inspect"),
                        action("destroy", ActionPhase.RESPONSE, ActionOutcome.HARMFUL, -10, null)));
    }

    @Test
    void acceptsValidDefinition() {
        assertThatCode(() -> validator.validate(valid())).doesNotThrowAnyException();
    }

    @Test
    void rejectsPointsThatContradictOutcome() {
        var def = definition(List.of(event("e1", true, null)),
                List.of(action("act-a", ActionPhase.INVESTIGATION, ActionOutcome.HARMFUL, 5, null),
                        action("act-b", ActionPhase.INVESTIGATION, ActionOutcome.EXPECTED, 10, null)));
        assertInvalid(def, "actions[0].points");
    }

    @Test
    void rejectsUnknownReferences() {
        var badAction = new ActionDef("act-a", "l", "d", ActionPhase.RESPONSE, ActionCategory.CONTAIN, "missing-vm",
                ActionOutcome.EXPECTED, 10, "missing-action", "ISOLATED", "r", "e");
        var def = definition(List.of(event("e1", true, null)), List.of(badAction));
        assertInvalid(def, "actions[0].targetResourceKey");
        assertInvalid(def, "actions[0].prerequisiteActionKey");
    }

    @Test
    void rejectsEventsRevealedByResponseActions() {
        var def = definition(
                List.of(event("e1", true, null), event("e2", false, "isolate")),
                List.of(action("isolate", ActionPhase.RESPONSE, ActionOutcome.EXPECTED, 10, null)));
        assertInvalid(def, "events[1].revealedByActionKey");
    }

    @Test
    void rejectsPrerequisiteCycles() {
        var def = definition(List.of(event("e1", true, null)),
                List.of(action("act-a", ActionPhase.INVESTIGATION, ActionOutcome.EXPECTED, 10, "act-b"),
                        action("act-b", ActionPhase.INVESTIGATION, ActionOutcome.EXPECTED, 10, "act-a")));
        assertInvalid(def, "actions");
    }

    @Test
    void rejectsDuplicateKeys() {
        var def = definition(List.of(event("e1", true, null), event("e1", false, null)),
                List.of(action("act-a", ActionPhase.INVESTIGATION, ActionOutcome.EXPECTED, 10, null)));
        assertInvalid(def, "events");
    }

    @Test
    void rejectsUnplayableScenario() {
        // no initially visible event, no evidence, no expected action
        var def = definition(List.of(event("e1", false, "act-a")),
                List.of(action("act-a", ActionPhase.INVESTIGATION, ActionOutcome.NEUTRAL, 0, null)));
        assertInvalid(def, "events");
        assertInvalid(def, "actions");
    }

    @Test
    void rejectsSystemEventsAndMissingEvidenceNote() {
        var system = new EventDef("e1", 0, EventType.SYSTEM, "x", Severity.LOW, null, "m", Map.of(), true, null, null);
        var def = definition(List.of(system),
                List.of(action("act-a", ActionPhase.INVESTIGATION, ActionOutcome.EXPECTED, 10, null)));
        assertInvalid(def, "events[0].type");
        assertInvalid(def, "events[0].evidenceNote");
    }

    @Test
    void rejectsBeanValidationViolations() {
        var def = new ScenarioDefinition("Invalid Slug!", "", "s", "d", Difficulty.BEGINNER, Category.NETWORK, 0,
                "e", "s", -1, 0, List.of(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), null);
        assertInvalid(def, "slug");
        assertInvalid(def, "estimatedMinutes");
    }

    private static void assertInvalid(ScenarioDefinition def, String field) {
        assertThatThrownBy(() -> validator.validate(def))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> org.assertj.core.api.Assertions.assertThat(((ApiException) ex).getFieldErrors())
                        .anyMatch(fe -> fe.field().equals(field)));
    }
}
