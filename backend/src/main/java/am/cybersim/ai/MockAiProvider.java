package am.cybersim.ai;

import am.cybersim.scenario.dto.ScenarioDefinition;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

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

    private String toJson(Object value) {
        return objectMapper.writeValueAsString(value);
    }
}
