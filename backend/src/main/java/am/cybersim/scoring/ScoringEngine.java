package am.cybersim.scoring;

import am.cybersim.scenario.ScenarioAction;
import am.cybersim.scenario.ScenarioEnums.ActionCategory;
import am.cybersim.scenario.ScenarioEnums.ActionOutcome;
import am.cybersim.scoring.ScoreResult.ItemKind;
import am.cybersim.scoring.ScoreResult.MissedAction;
import am.cybersim.scoring.ScoreResult.ScoreItem;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Deterministic scoring engine (ADR-6) — AI is never involved in grading.
 *
 * <p>Inputs: the scenario's action catalogue and scoring configuration, the actions the student performed
 * (in order) and the number of hints used. Output: {@link ScoreResult}. The same input always gives the
 * same result, which makes grades reproducible and explainable to students.
 *
 * <p>Rules (configurable per scenario/action, see 08-simulation-engine §5):
 * <ul>
 *   <li>EXPECTED action, first time: +points; before its prerequisite: +points − outOfOrderPenalty (min 0)</li>
 *   <li>NEUTRAL action: 0 (reported as unnecessary)</li>
 *   <li>HARMFUL action: negative points</li>
 *   <li>repeated action: 0</li>
 *   <li>each AI hint: −hintPenalty</li>
 * </ul>
 */
@Component
public class ScoringEngine {

    /** One performed action as the scoring engine sees it (snapshot taken when the action was performed). */
    public record PerformedAction(String actionKey, String label, ActionCategory category,
                                  ActionOutcome outcome, boolean duplicate, boolean outOfOrder, int pointsAwarded) {
    }

    /**
     * Points for a single action at the moment it is performed. Used by the simulation engine so that the
     * points shown in the action history and the final score always agree.
     */
    public int pointsFor(ScenarioAction action, boolean outOfOrder, boolean duplicate, int outOfOrderPenalty) {
        if (duplicate) {
            return 0;
        }
        return switch (action.getOutcome()) {
            case EXPECTED -> outOfOrder ? Math.max(0, action.getPoints() - outOfOrderPenalty) : action.getPoints();
            case NEUTRAL -> 0;
            case HARMFUL -> action.getPoints();
        };
    }

    public ScoreResult score(List<ScenarioAction> catalogue, List<PerformedAction> performed, int hintsUsed,
                             int hintPenalty) {
        List<ScoreItem> items = new ArrayList<>();
        Set<String> performedKeys = new HashSet<>();
        int raw = 0;

        for (PerformedAction p : performed) {
            performedKeys.add(p.actionKey());
            ItemKind kind;
            String note;
            if (p.duplicate()) {
                kind = ItemKind.DUPLICATE;
                note = "Repeated action — no additional points";
            } else {
                kind = switch (p.outcome()) {
                    case EXPECTED -> p.outOfOrder() ? ItemKind.OUT_OF_ORDER : ItemKind.EXPECTED;
                    case NEUTRAL -> ItemKind.NEUTRAL;
                    case HARMFUL -> ItemKind.HARMFUL;
                };
                note = switch (kind) {
                    case OUT_OF_ORDER -> "Correct action, but performed before its recommended prerequisite";
                    case NEUTRAL -> "Unnecessary action";
                    case HARMFUL -> "Incorrect or destructive action";
                    default -> "Correct action";
                };
            }
            raw += p.pointsAwarded();
            items.add(new ScoreItem(p.actionKey(), p.label(), p.category(), kind, p.pointsAwarded(), note));
        }

        int hintPenaltyTotal = hintsUsed * hintPenalty;
        if (hintPenaltyTotal > 0) {
            raw -= hintPenaltyTotal;
            items.add(new ScoreItem(null, hintsUsed + " AI hint(s) used", null, ItemKind.HINT_PENALTY,
                    -hintPenaltyTotal, hintPenalty + " point(s) per hint"));
        }

        List<MissedAction> missed = catalogue.stream()
                .filter(a -> a.getOutcome() == ActionOutcome.EXPECTED)
                .filter(a -> !performedKeys.contains(a.getActionKey()))
                .map(a -> new MissedAction(a.getActionKey(), a.getLabel(), a.getCategory(), a.getPoints(),
                        a.getExplanation()))
                .toList();

        int max = catalogue.stream()
                .filter(a -> a.getOutcome() == ActionOutcome.EXPECTED)
                .mapToInt(ScenarioAction::getPoints)
                .sum();
        int percent = max <= 0 ? 0 : Math.clamp(Math.round(raw * 100f / max), 0, 100);
        return new ScoreResult(raw, max, percent, hintPenaltyTotal, items, missed);
    }
}
