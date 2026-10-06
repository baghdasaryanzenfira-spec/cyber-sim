package am.cybersim.scoring;

import am.cybersim.scenario.ScenarioEnums.ActionCategory;

import java.util.List;

/**
 * Output of the {@link ScoringEngine}. Stored as a frozen snapshot in {@code simulation_results}.
 *
 * @param rawScore         sum of awarded points minus hint penalties (may be negative)
 * @param maxScore         sum of the points of all EXPECTED actions of the scenario
 * @param scorePercent     rawScore relative to maxScore, clamped to 0..100
 * @param hintPenaltyTotal points deducted for AI hints
 * @param items            one line per scored element, in the order the student performed the actions
 * @param missedActions    EXPECTED actions the student never performed
 */
public record ScoreResult(int rawScore, int maxScore, int scorePercent, int hintPenaltyTotal,
                          List<ScoreItem> items, List<MissedAction> missedActions) {

    public enum ItemKind { EXPECTED, OUT_OF_ORDER, NEUTRAL, HARMFUL, DUPLICATE, HINT_PENALTY }

    public record ScoreItem(String actionKey, String label, ActionCategory category, ItemKind kind, int points,
                            String note) {
    }

    public record MissedAction(String actionKey, String label, ActionCategory category, int points,
                               String explanation) {
    }
}
