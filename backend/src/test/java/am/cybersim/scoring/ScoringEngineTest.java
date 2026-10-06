package am.cybersim.scoring;

import am.cybersim.scenario.ScenarioAction;
import am.cybersim.scenario.ScenarioEnums.ActionCategory;
import am.cybersim.scenario.ScenarioEnums.ActionOutcome;
import am.cybersim.scoring.ScoreResult.ItemKind;
import am.cybersim.scoring.ScoringEngine.PerformedAction;
import am.cybersim.support.TestScenarios;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScoringEngineTest {

    ScoringEngine engine = new ScoringEngine();
    List<ScenarioAction> catalogue = TestScenarios.scenario().getActions();

    static PerformedAction expected(String key, int points) {
        return new PerformedAction(key, key, ActionCategory.INSPECT, ActionOutcome.EXPECTED, false, false, points);
    }

    @Test
    void perfectRunScoresHundredPercent() {
        var result = engine.score(catalogue,
                List.of(expected("inspect-log", 10), expected("identify-vm", 20), expected("isolate-vm", 30)), 0, 2);
        assertThat(result.maxScore()).isEqualTo(60);
        assertThat(result.rawScore()).isEqualTo(60);
        assertThat(result.scorePercent()).isEqualTo(100);
        assertThat(result.missedActions()).isEmpty();
    }

    @Test
    void missedActionsHarmfulActionsAndHintsReduceTheScore() {
        var harmful = new PerformedAction("delete-vm", "Delete", ActionCategory.CONTAIN, ActionOutcome.HARMFUL, false, false, -10);
        var neutral = new PerformedAction("check-other", "Check", ActionCategory.INSPECT, ActionOutcome.NEUTRAL, false, false, 0);
        var duplicate = new PerformedAction("inspect-log", "Inspect", ActionCategory.INSPECT, ActionOutcome.EXPECTED, true, false, 0);

        var result = engine.score(catalogue, List.of(expected("inspect-log", 10), duplicate, neutral, harmful), 2, 2);

        // 10 - 10 - 2*2 = -4 → clamped to 0 %
        assertThat(result.rawScore()).isEqualTo(-4);
        assertThat(result.scorePercent()).isZero();
        assertThat(result.hintPenaltyTotal()).isEqualTo(4);
        assertThat(result.missedActions()).extracting(ScoreResult.MissedAction::actionKey)
                .containsExactly("identify-vm", "isolate-vm");
        assertThat(result.items()).extracting(ScoreResult.ScoreItem::kind)
                .containsExactly(ItemKind.EXPECTED, ItemKind.DUPLICATE, ItemKind.NEUTRAL, ItemKind.HARMFUL, ItemKind.HINT_PENALTY);
    }

    @Test
    void percentIsRoundedAndOutOfOrderIsReported() {
        var outOfOrder = new PerformedAction("isolate-vm", "Isolate", ActionCategory.CONTAIN, ActionOutcome.EXPECTED, false, true, 25);
        var result = engine.score(catalogue, List.of(outOfOrder), 0, 2);
        assertThat(result.scorePercent()).isEqualTo(42); // 25/60 = 41.67 → 42
        assertThat(result.items().getFirst().kind()).isEqualTo(ItemKind.OUT_OF_ORDER);
    }

    @Test
    void pointsForAppliesOrderPenaltyButNeverBelowZero() {
        ScenarioAction isolate = catalogue.stream().filter(a -> a.getActionKey().equals("isolate-vm")).findFirst().orElseThrow();
        assertThat(engine.pointsFor(isolate, false, false, 5)).isEqualTo(30);
        assertThat(engine.pointsFor(isolate, true, false, 5)).isEqualTo(25);
        assertThat(engine.pointsFor(isolate, true, false, 50)).isZero();
        assertThat(engine.pointsFor(isolate, false, true, 5)).isZero();
    }

    @Test
    void scoringIsDeterministic() {
        var actions = List.of(expected("inspect-log", 10), expected("isolate-vm", 30));
        assertThat(engine.score(catalogue, actions, 1, 2)).isEqualTo(engine.score(catalogue, actions, 1, 2));
    }
}
