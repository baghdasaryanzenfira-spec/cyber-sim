package am.cybersim.ai.dto;

import java.util.List;

/**
 * Structured AI review of one submitted exam attempt, as returned by the model (ADR-13).
 * {@code rating} is the reviewer's advisory 0–100 assessment of the student's methodology; the authoritative
 * grade stays the deterministic verified score and the two are shown side by side, never merged.
 */
public record AiReview(int rating, String message, List<String> strengths, List<String> mistakes,
                       List<String> recommendations) {

    public AiReview {
        strengths = strengths == null ? List.of() : strengths;
        mistakes = mistakes == null ? List.of() : mistakes;
        recommendations = recommendations == null ? List.of() : recommendations;
    }
}
