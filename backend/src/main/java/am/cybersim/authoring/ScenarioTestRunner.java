package am.cybersim.authoring;

import am.cybersim.scenario.ScenarioEnums.ActionOutcome;
import am.cybersim.scenario.dto.ScenarioDefinition;
import am.cybersim.scenario.dto.ScenarioDefinition.ActionDef;
import am.cybersim.scenario.dto.ScenarioDefinition.EventDef;
import am.cybersim.scenario.dto.ScenarioDefinition.ResourceDef;
import am.cybersim.scoring.ScoreResult;
import am.cybersim.scoring.ScoringEngine;
import am.cybersim.scoring.ScoringEngine.PerformedAction;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Scenario test runner (authoring step "Test runner executes the correct path and the dangerous path").
 *
 * <p>Plays the scenario in memory exactly like a trainee would - same prerequisite, evidence-disclosure,
 * resource-effect and scoring rules, same {@link ScoringEngine} - but without any database or user:
 * <ul>
 *   <li><b>Correct path</b>: every EXPECTED action in dependency order. Must reach 100 %, disclose all
 *       evidence and change at least one resource.</li>
 *   <li><b>Dangerous path</b>: every HARMFUL action. Must be penalised, must not resolve the incident and must
 *       not reproduce the state that the correct fix produces.</li>
 * </ul>
 * Deterministic and side-effect free, so it can run on every save and as a publishing gate.
 */
@Component
public class ScenarioTestRunner {

    public record Check(String name, boolean passed, String message) {
    }

    public record Step(int sequence, String actionKey, String label, ActionOutcome outcome, int points,
                       boolean outOfOrder, boolean duplicate, int revealedEvents, String resourceEffect) {
    }

    public record PathResult(String path, String description, boolean passed, int rawScore, int maxScore,
                             int scorePercent, int evidenceRevealed, int evidenceTotal, List<Step> steps,
                             List<Check> checks) {
    }

    public record TestRunReport(boolean passed, List<PathResult> paths) {
    }

    private final ScoringEngine scoring;

    public ScenarioTestRunner(ScoringEngine scoring) {
        this.scoring = scoring;
    }

    public TestRunReport run(ScenarioDefinition def) {
        PathResult correct = correctPath(def);
        PathResult dangerous = dangerousPath(def);
        return new TestRunReport(correct.passed() && dangerous.passed(), List.of(correct, dangerous));
    }

    // ------------------------------------------------------------------ correct path

    private PathResult correctPath(ScenarioDefinition def) {
        List<ActionDef> order = ScenarioGraph.topologicalOrder(def, ScenarioGraph::isExpected);
        Play play = play(def, order);
        List<Check> checks = new ArrayList<>();
        long expectedTotal = def.actions().stream().filter(ScenarioGraph::isExpected).count();

        checks.add(check("All correct actions can be performed", order.size() == expectedTotal,
                order.size() + " of " + expectedTotal + " correct actions are performable"
                        + (order.size() == expectedTotal ? "" : " (the rest are stuck in a prerequisite cycle)")));
        checks.add(check("Performed in valid order, no penalties",
                play.steps.stream().noneMatch(s -> s.outOfOrder() || s.duplicate()),
                "dependency order is respected"));
        checks.add(check("Maximum score is reached", play.score.scorePercent() == 100,
                "score " + play.score.rawScore() + "/" + play.score.maxScore() + " (" + play.score.scorePercent() + " %)"));
        checks.add(check("All evidence is discoverable", play.evidenceRevealed == play.evidenceTotal,
                play.evidenceRevealed + " of " + play.evidenceTotal + " evidence events revealed"));
        checks.add(check("Incident ends in a changed state", !play.changedResources.isEmpty(),
                play.changedResources.isEmpty() ? "no resource changes status - the response has no visible effect"
                        : "changed: " + String.join(", ", play.changedResources)));
        checks.add(check("No correct action is missed", play.score.missedActions().isEmpty(),
                play.score.missedActions().size() + " missed"));
        return result("CORRECT", "Every expected action in dependency order", def, play, checks);
    }

    // ------------------------------------------------------------------ dangerous path

    private PathResult dangerousPath(ScenarioDefinition def) {
        List<ActionDef> order = ScenarioGraph.topologicalOrder(def, a -> a.outcome() == ActionOutcome.HARMFUL);
        Play play = play(def, order);
        List<Check> checks = new ArrayList<>();

        checks.add(check("Dangerous actions exist", !order.isEmpty(), order.size() + " harmful action(s)"));
        checks.add(check("Every harmful action is penalised",
                !play.steps.isEmpty() && play.steps.stream().allMatch(s -> s.points() < 0),
                "points: " + play.steps.stream().map(s -> String.valueOf(s.points())).toList()));
        checks.add(check("Score stays failing", play.score.scorePercent() < 50 && play.score.rawScore() <= 0,
                "score " + play.score.rawScore() + "/" + play.score.maxScore() + " (" + play.score.scorePercent() + " %)"));
        checks.add(check("Incident is not resolved",
                play.score.missedActions().size() == def.actions().stream().filter(ScenarioGraph::isExpected).count(),
                play.score.missedActions().size() + " correct actions still missing"));

        Map<String, String> fixedStates = new HashMap<>();
        def.actions().stream().filter(ScenarioGraph::isExpected)
                .filter(a -> !ScenarioGraph.blank(a.targetResourceKey()) && !ScenarioGraph.blank(a.effectStatus()))
                .forEach(a -> fixedStates.put(a.targetResourceKey(), a.effectStatus()));
        List<String> fakeFixes = order.stream()
                .filter(a -> !ScenarioGraph.blank(a.targetResourceKey()) && !ScenarioGraph.blank(a.effectStatus()))
                .filter(a -> a.effectStatus().equals(fixedStates.get(a.targetResourceKey())))
                .map(ActionDef::label).toList();
        checks.add(check("A harmful action does not look like the fix", fakeFixes.isEmpty(),
                fakeFixes.isEmpty() ? "no harmful action produces a correct end state"
                        : "same end state as the correct response: " + fakeFixes));

        List<String> unexplained = order.stream().filter(a -> a.explanation().strip().length() < 20)
                .map(ActionDef::label).toList();
        checks.add(check("Every harmful action explains why it is wrong", unexplained.isEmpty(),
                unexplained.isEmpty() ? "explanations present" : "missing/too short: " + unexplained));
        return result("DANGEROUS", "Every harmful action", def, play, checks);
    }

    // ------------------------------------------------------------------ in-memory play

    private record Play(List<Step> steps, ScoreResult score, int evidenceRevealed, int evidenceTotal,
                        Set<String> changedResources) {
    }

    private Play play(ScenarioDefinition def, List<ActionDef> order) {
        Set<String> applied = new HashSet<>();
        Set<String> revealed = new HashSet<>();
        def.events().stream().filter(e -> ScenarioGraph.blank(e.revealedByActionKey())).forEach(e -> revealed.add(e.key()));
        Map<String, String> status = new LinkedHashMap<>();
        for (ResourceDef r : def.resources()) {
            status.put(r.key(), r.status());
        }
        Map<String, String> initial = new LinkedHashMap<>(status);

        List<Step> steps = new ArrayList<>();
        List<PerformedAction> performed = new ArrayList<>();
        for (ActionDef a : order) {
            boolean duplicate = applied.contains(a.key());
            boolean outOfOrder = !duplicate && !ScenarioGraph.blank(a.prerequisiteActionKey())
                    && !applied.contains(a.prerequisiteActionKey());
            int points = scoring.pointsFor(a, outOfOrder, duplicate, def.outOfOrderPenalty());
            int newlyRevealed = 0;
            String effect = null;
            if (!duplicate) {
                applied.add(a.key());
                for (EventDef e : def.events()) {
                    if (a.key().equals(e.revealedByActionKey()) && revealed.add(e.key())) {
                        newlyRevealed++;
                    }
                }
                if (!ScenarioGraph.blank(a.effectStatus()) && !ScenarioGraph.blank(a.targetResourceKey())
                        && status.containsKey(a.targetResourceKey())) {
                    effect = a.targetResourceKey() + ": " + status.get(a.targetResourceKey()) + " -> " + a.effectStatus();
                    status.put(a.targetResourceKey(), a.effectStatus());
                }
            }
            steps.add(new Step(steps.size() + 1, a.key(), a.label(), a.outcome(), points, outOfOrder, duplicate,
                    newlyRevealed, effect));
            performed.add(new PerformedAction(a.key(), a.label(), a.category(), a.outcome(), duplicate, outOfOrder, points));
        }
        ScoreResult score = scoring.score(def.actions(), performed, 0, def.hintPenalty());
        Set<String> changed = new HashSet<>();
        status.forEach((k, v) -> {
            if (!v.equals(initial.get(k))) {
                changed.add(k);
            }
        });
        int evidenceTotal = (int) def.events().stream().filter(EventDef::evidence).count();
        int evidenceRevealed = (int) def.events().stream().filter(e -> e.evidence() && revealed.contains(e.key())).count();
        return new Play(steps, score, evidenceRevealed, evidenceTotal, changed);
    }

    private static PathResult result(String path, String description, ScenarioDefinition def, Play play,
                                     List<Check> checks) {
        boolean passed = checks.stream().allMatch(Check::passed);
        return new PathResult(path, description, passed, play.score.rawScore(), play.score.maxScore(),
                play.score.scorePercent(), play.evidenceRevealed, play.evidenceTotal, play.steps, checks);
    }

    private static Check check(String name, boolean passed, String message) {
        return new Check(name, passed, message);
    }
}
