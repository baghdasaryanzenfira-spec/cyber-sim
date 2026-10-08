package am.cybersim.integration.dto;

import am.cybersim.ai.dto.AiDtos.AiSource;
import am.cybersim.scenario.ScenarioEnums.ActionOutcome;
import am.cybersim.scenario.ScenarioEnums.Category;
import am.cybersim.scenario.ScenarioEnums.Difficulty;
import am.cybersim.scenario.dto.ScenarioDefinition;
import am.cybersim.scoring.ScoreResult.MissedAction;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** DTOs of the learner-module service API and the admin exam review (ADR-13). */
public final class IntegrationDtos {

    private IntegrationDtos() {
    }

    // ------------------------------------------------------------------ learner: published content

    public record PublishedScenarioSummary(String slug, String title, String summary, Difficulty difficulty,
                                           Category category, int estimatedMinutes, int publishedVersion,
                                           int qualityScore, int maxScore) {
    }

    public record PublishedScenarioDetail(String slug, int version, int qualityScore,
                                          ScenarioDefinition definition) {
    }

    // ------------------------------------------------------------------ learner: attempt submission

    public record SubmittedAction(
            @NotBlank @Size(max = 100) String actionKey,
            @Size(max = 500) String note) {
    }

    public record SubmitAttemptRequest(
            @NotBlank @Size(max = 100) String externalId,
            @NotBlank @Size(max = 100) String scenarioSlug,
            /* null = the currently published version */
            @Min(1) Integer scenarioVersion,
            @NotBlank @Size(max = 100) String studentRef,
            @Size(max = 200) String studentName,
            Instant startedAt,
            @NotNull Instant completedAt,
            @Min(0) @Max(1000) int hintsUsed,
            @NotEmpty @Size(max = 500) List<@Valid @NotNull SubmittedAction> actions,
            @Min(0) @Max(100) Integer claimedScorePercent) {
    }

    // ------------------------------------------------------------------ verification (ours, authoritative)

    public record VerificationStep(int sequence, String actionKey, String label, ActionOutcome outcome,
                                   int points, boolean outOfOrder, boolean duplicate) {
    }

    public record Verification(int scorePercent, int rawScore, int maxScore, int hintsUsed, int hintPenaltyTotal,
                               int evidenceRevealed, int evidenceTotal, List<VerificationStep> steps,
                               List<MissedAction> missedActions) {
    }

    /** What the learner module gets back: our replayed result, never an echo of the claimed score. */
    public record AttemptReceipt(String externalId, String scenarioSlug, int scenarioVersion,
                                 int verifiedScorePercent, Boolean scoreMatches, Verification verification,
                                 StoredReview review, Instant submittedAt) {
    }

    // ------------------------------------------------------------------ AI review (advisory)

    /** The stored AI review; {@code rating} is the AI's advisory assessment, distinct from the verified score. */
    public record StoredReview(int rating, String message, List<String> strengths, List<String> mistakes,
                               List<String> recommendations, AiSource source) {
    }

    // ------------------------------------------------------------------ admin views

    public record AdminAttemptRow(Long id, String externalId, String studentRef, String studentName,
                                  Long scenarioId, String scenarioTitle, int scenarioVersion,
                                  Integer claimedScore, int verifiedScore, Boolean scoreMatches,
                                  boolean reviewed, Integer reviewRating, int hintsUsed, Instant submittedAt) {
    }

    public record AdminAttemptDetail(AdminAttemptRow attempt, List<SubmittedAction> submittedActions,
                                     Verification verification, StoredReview review, Instant reviewedAt) {
    }

    public record PageView<T>(List<T> items, long totalItems, int page, int size) {
    }
}
