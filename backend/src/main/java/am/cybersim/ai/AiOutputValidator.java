package am.cybersim.ai;

import am.cybersim.ai.dto.AiReview;
import am.cybersim.scenario.dto.ScenarioDefinition;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.regex.Pattern;

/**
 * Validates AI output before the application uses it. AI output is untrusted (requirement §9):
 * anything that fails here is discarded and the deterministic fallback is used instead.
 *
 * <ul>
 *   <li>Text answers: not blank, length limit, control characters removed.</li>
 *   <li>JSON answers: parsed into the expected record; the scenario rules are enforced by the caller.</li>
 * </ul>
 */
@Component
public class AiOutputValidator {

    static final int MAX_TRANSLATION_CHARS = 12_000;
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}&&[^\n\t]]");
    private static final Pattern CODE_FENCE = Pattern.compile("^```(?:json)?\\s*|\\s*```$");

    private final ObjectMapper objectMapper;

    public AiOutputValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Only parsing here; the scenario rules are enforced by the callers ({@code ScenarioVariationService}, {@code ScenarioGeneratorService}). */
    public ScenarioDefinition parseScenarioDefinition(String json) {
        return parse(json, ScenarioDefinition.class);
    }

    /**
     * A translation may legitimately be longer than its source (Armenian runs longer than English), so the only
     * limit is an absolute one; a response far longer than the source means the model explained instead of
     * translating and is rejected.
     */
    /**
     * The review is advisory text shown to administrators and forwarded to the learner module, so the same
     * discipline as every AI output: parsed, clamped in size, and a rating outside 0–100 is treated as a failed
     * response (the gateway then falls back to the deterministic mock review).
     */
    public AiReview validateReview(String json) {
        AiReview review = parse(json, AiReview.class);
        if (review.rating() < 0 || review.rating() > 100) {
            throw new AiProviderException("Review rating " + review.rating() + " is outside 0-100");
        }
        return new AiReview(review.rating(), validateText(review.message(), 2000),
                validateItems(review.strengths()), validateItems(review.mistakes()),
                validateItems(review.recommendations()));
    }

    private static java.util.List<String> validateItems(java.util.List<String> items) {
        return items.stream().limit(5).map(i -> validateText(i, 300)).toList();
    }

    public String validateTranslation(String text, String source) {
        String clean = validateText(text, MAX_TRANSLATION_CHARS);
        if (clean.length() > Math.max(200, source.length() * 3)) {
            throw new AiProviderException("Translation is implausibly long for its source");
        }
        return clean;
    }

    private <T> T parse(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            throw new AiProviderException("Empty AI response");
        }
        String stripped = CODE_FENCE.matcher(json.strip()).replaceAll("");
        try {
            T value = objectMapper.readValue(stripped, type);
            if (value == null) {
                throw new AiProviderException("AI response parsed to null");
            }
            return value;
        } catch (JacksonException e) {
            throw new AiProviderException("AI response is not valid " + type.getSimpleName() + " JSON");
        }
    }

    private static String validateText(String text, int maxChars) {
        if (text == null || text.isBlank()) {
            throw new AiProviderException("Empty AI response");
        }
        String clean = CONTROL_CHARS.matcher(text).replaceAll("").strip();
        if (clean.length() > maxChars) {
            throw new AiProviderException("AI response too long (" + clean.length() + " chars)");
        }
        return clean;
    }
}
