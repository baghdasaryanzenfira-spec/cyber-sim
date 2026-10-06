package am.cybersim.ai.dto;

import am.cybersim.scenario.ScenarioEnums.Category;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** API and structured-output models of the AI module. */
public final class AiDtos {

    private AiDtos() {
    }

    /** Where an AI answer came from: real model, configured mock, or mock used because the model failed. */
    public enum AiSource { AI, MOCK, FALLBACK }

    public record AskRequest(@NotBlank @Size(max = 500) String question) {
    }

    public record AssistantReply(String type, String text, AiSource source, int hintsUsed, Instant createdAt) {
    }

    public record AssistantMessage(Long id, String type, String question, String answer, AiSource source,
                                   Instant createdAt) {
    }

    public record Recommendation(String topic, Category category, String reason) {
    }

    /** Structured-output schema for the recommendation task. */
    public record RecommendationList(List<Recommendation> recommendations) {
    }

    public record RecommendationsView(List<Recommendation> recommendations, AiSource source) {
    }
}
