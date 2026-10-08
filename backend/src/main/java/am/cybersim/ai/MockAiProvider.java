package am.cybersim.ai;

import am.cybersim.ai.dto.AiReview;
import am.cybersim.ai.dto.ReviewSubject;
import am.cybersim.scenario.ScenarioEnums.ActionOutcome;
import am.cybersim.scenario.dto.ScenarioDefinition;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.ArrayList;

/**
 * Deterministic, offline "AI" used when no API key is configured and as the fallback when the real model
 * fails. It works on the same structured payloads as the real provider, so the rest of the platform cannot tell
 * the difference except through the {@code source} field.
 */
@Component
public class MockAiProvider implements AiProvider {

    static final String MODEL = "cybersim-mock-1";

    private final ObjectMapper objectMapper;

    public MockAiProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Type type() {
        return Type.MOCK;
    }

    @Override
    public AiResponse complete(AiRequest request) {
        String text = switch (request.payload()) {
            case AiPayload.Variation v -> toJson(variation(v.original(), v.newSlug()));
            case AiPayload.Generation g -> toJson(g.draft());
            case AiPayload.Translation t -> t.text();
            case AiPayload.Review r -> toJson(review(r.subject()));
        };
        return new AiResponse(text, MODEL, null, null);
    }

    // ------------------------------------------------------------------ variation

    /** Deterministic variation: new slug/title and shifted documentation IP addresses; structure unchanged. */
    ScenarioDefinition variation(ScenarioDefinition d, String newSlug) {
        String json = toJson(d)
                .replace("203.0.113.", "192.0.2.")
                .replace("198.51.100.", "203.0.113.");
        ScenarioDefinition copy = objectMapper.readValue(json, ScenarioDefinition.class);
        return new ScenarioDefinition(newSlug, d.title() + " (variant)", copy.summary(), copy.description(),
                copy.difficulty(), copy.category(), copy.estimatedMinutes(), copy.incidentExplanation(),
                copy.recommendedSolution(), copy.hintPenalty(), copy.outOfOrderPenalty(),
                copy.learningObjectives(), copy.resources(), copy.events(), copy.actions(), copy.hints());
    }

    /**
     * Deterministic review built from the verified replay: the rating equals the verified score, strengths are
     * the correctly performed actions, mistakes are harmful/out-of-order/missed ones. Honest and reproducible —
     * the real model adds nuance, the mock adds none.
     */
    public AiReview review(ReviewSubject s) {
        List<String> strengths = s.steps().stream()
                .filter(st -> st.outcome() == ActionOutcome.EXPECTED && !st.duplicate() && !st.outOfOrder())
                .map(st -> "You performed \"" + st.label() + "\".")
                .limit(5).toList();
        List<String> mistakes = new ArrayList<>();
        s.steps().stream().filter(st -> st.outcome() == ActionOutcome.HARMFUL).limit(2)
                .forEach(st -> mistakes.add("\"" + st.label() + "\" is a harmful response (" + st.points() + " points)."));
        s.steps().stream().filter(ReviewSubject.Step::outOfOrder).limit(1)
                .forEach(st -> mistakes.add("\"" + st.label() + "\" was performed before its prerequisite."));
        s.missed().stream().limit(5 - Math.min(mistakes.size(), 5))
                .forEach(m -> mistakes.add("Missed: \"" + m.label() + "\" — " + m.explanation()));
        List<String> recommendations = new ArrayList<>();
        if (!s.missed().isEmpty()) {
            recommendations.add("Rehearse the full response path: " + s.missed().size()
                    + " expected step(s) were missed.");
        }
        if (s.steps().stream().anyMatch(st -> st.outcome() == ActionOutcome.HARMFUL)) {
            recommendations.add("Review evidence-preserving containment: prefer isolation over destructive actions.");
        }
        if (s.evidenceTotal() > 0 && s.evidenceRevealed() < s.evidenceTotal()) {
            recommendations.add("Practise working through all log sources before responding ("
                    + s.evidenceRevealed() + " of " + s.evidenceTotal() + " evidence items were uncovered).");
        }
        String message = "The verified score of this attempt is " + s.verifiedScorePercent() + "/100. "
                + (s.missed().isEmpty() && mistakes.isEmpty()
                ? "The response followed the expected investigation and containment order — well done."
                : "The review below lists what worked and what to focus on next time.")
                + (s.hintsUsed() > 0 ? " " + s.hintsUsed() + " hint(s) were used." : "");
        return new AiReview(s.verifiedScorePercent(), message, strengths, mistakes, recommendations);
    }

    private String toJson(Object value) {
        return objectMapper.writeValueAsString(value);
    }
}
