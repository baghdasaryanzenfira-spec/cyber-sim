package am.cybersim.support;

import am.cybersim.scenario.Scenario;
import am.cybersim.scenario.ScenarioEnums.ActionCategory;
import am.cybersim.scenario.ScenarioEnums.ActionOutcome;
import am.cybersim.scenario.ScenarioEnums.ActionPhase;
import am.cybersim.scenario.ScenarioEnums.Category;
import am.cybersim.scenario.ScenarioEnums.Difficulty;
import am.cybersim.scenario.ScenarioEnums.EventType;
import am.cybersim.scenario.ScenarioEnums.ResourceType;
import am.cybersim.scenario.ScenarioEnums.Severity;
import am.cybersim.scenario.ScenarioMapper;
import am.cybersim.scenario.dto.ScenarioDefinition;
import am.cybersim.scenario.dto.ScenarioDefinition.ActionDef;
import am.cybersim.scenario.dto.ScenarioDefinition.EventDef;
import am.cybersim.scenario.dto.ScenarioDefinition.ResourceDef;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * A small in-memory scenario for unit tests (no database).
 *
 * <pre>
 * inspect-log  (INVESTIGATION, EXPECTED +10)  reveals "log-success"
 * identify-vm  (INVESTIGATION, EXPECTED +20)  prerequisite inspect-log, VM → COMPROMISED
 * isolate-vm   (RESPONSE, EXPECTED +30)       prerequisite identify-vm, VM → ISOLATED
 * check-other  (INVESTIGATION, NEUTRAL 0)
 * delete-vm    (RESPONSE, HARMFUL -10)        VM → TERMINATED
 * max score 60, hint penalty 2, out-of-order penalty 5
 * </pre>
 */
public final class TestScenarios {

    private TestScenarios() {
    }

    public static ScenarioDefinition definition() {
        return new ScenarioDefinition("unit-test-scenario", "Unit test incident", "Summary", "Description",
                Difficulty.BEGINNER, Category.NETWORK, 10, "Explanation", "Solution", 2, 5, true,
                List.of("Objective 1"),
                List.of(new ResourceDef("vm", ResourceType.VIRTUAL_MACHINE, "web-01", "eu", "RUNNING", Map.of("ip", "10.0.0.1")),
                        new ResourceDef("vm-other", ResourceType.VIRTUAL_MACHINE, "app-02", "eu", "RUNNING", Map.of())),
                List.of(new EventDef("alert", 100, EventType.ALERT, "detector", Severity.HIGH, "vm", "Brute force alert",
                                Map.of(), true, "alert note", null),
                        new EventDef("log-noise", 50, EventType.LOG, "monitor", Severity.INFO, "vm-other", "Health OK",
                                Map.of(), false, null, null),
                        new EventDef("log-success", 90, EventType.LOG, "auth.log", Severity.CRITICAL, "vm",
                                "Accepted password", Map.of(), true, "success note", "inspect-log")),
                List.of(action("inspect-log", ActionPhase.INVESTIGATION, ActionCategory.INSPECT, ActionOutcome.EXPECTED, 10, null, null),
                        action("identify-vm", ActionPhase.INVESTIGATION, ActionCategory.IDENTIFY, ActionOutcome.EXPECTED, 20, "inspect-log", "COMPROMISED"),
                        action("isolate-vm", ActionPhase.RESPONSE, ActionCategory.CONTAIN, ActionOutcome.EXPECTED, 30, "identify-vm", "ISOLATED"),
                        new ActionDef("check-other", "Check other VM", "d", ActionPhase.INVESTIGATION, ActionCategory.INSPECT,
                                "vm-other", ActionOutcome.NEUTRAL, 0, null, null, "ok", "not needed"),
                        action("delete-vm", ActionPhase.RESPONSE, ActionCategory.CONTAIN, ActionOutcome.HARMFUL, -10, null, "TERMINATED")),
                List.of("General hint", "Specific hint"));
    }

    private static ActionDef action(String key, ActionPhase phase, ActionCategory category, ActionOutcome outcome,
                                    int points, String prerequisite, String effect) {
        return new ActionDef(key, "Label " + key, "Description", phase, category, "vm", outcome, points,
                prerequisite, effect, "Result of " + key, "Explanation of " + key);
    }

    public static Scenario scenario() {
        Scenario scenario = new Scenario("unit-test-scenario", Instant.parse("2026-01-01T00:00:00Z"));
        new ScenarioMapper().applyDefinition(scenario, definition());
        return scenario;
    }
}
