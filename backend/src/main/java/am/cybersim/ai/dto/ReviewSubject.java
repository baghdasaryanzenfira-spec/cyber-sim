package am.cybersim.ai.dto;

import am.cybersim.scenario.ScenarioEnums.ActionOutcome;

import java.util.List;

/**
 * The facts of one submitted attempt, prepared for the review prompt. Deliberately contains only data this
 * platform produced itself (labels and outcomes come from the scenario definition, the scores from the
 * deterministic replay) — the learner module's free-text notes are never forwarded to the model.
 */
public record ReviewSubject(String studentName, int verifiedScorePercent, Integer claimedScorePercent,
                            int hintsUsed, int evidenceRevealed, int evidenceTotal,
                            List<Step> steps, List<Missed> missed) {

    public record Step(int sequence, String label, ActionOutcome outcome, int points,
                       boolean outOfOrder, boolean duplicate) {
    }

    public record Missed(String label, int points, String explanation) {
    }
}
