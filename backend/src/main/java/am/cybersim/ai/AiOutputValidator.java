package am.cybersim.ai;

import am.cybersim.ai.context.SimulationSnapshot;
import am.cybersim.ai.dto.AiDtos.Recommendation;
import am.cybersim.ai.dto.AiDtos.RecommendationList;
import am.cybersim.ai.dto.AiFeedback;
import am.cybersim.scenario.dto.ScenarioDefinition;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Validates AI output before the application uses it. AI output is untrusted (requirement §9):
 * anything that fails here is discarded and the deterministic fallback is used instead.
 *
 * <ul>
 *   <li>Text answers: not blank, length limit, control characters removed.</li>
 *   <li>Hints: additionally a <b>solution-leak check</b> — a hint that names two or more of the remaining
 *       expected actions verbatim is rejected, because it would give away the solution.</li>
 *   <li>JSON answers: parsed into the expected record; size limits per field.</li>
 * </ul>
 */
@Component
public class AiOutputValidator {

    static final int MAX_HINT_CHARS = 800;
    static final int MAX_ANSWER_CHARS = 2000;
    static final int MAX_TRANSLATION_CHARS = 12_000;
    static final int MAX_LIST_ITEMS = 8;
    static final int MAX_ITEM_CHARS = 500;
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}&&[^\n\t]]");
    private static final Pattern CODE_FENCE = Pattern.compile("^```(?:json)?\\s*|\\s*```$");

    private final ObjectMapper objectMapper;

    public AiOutputValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String validateHint(String text, SimulationSnapshot snapshot) {
        String clean = validateText(text, MAX_HINT_CHARS);
        String lower = clean.toLowerCase(Locale.ROOT);
        long leakedLabels = snapshot.remainingExpected().stream()
                .filter(a -> lower.contains(a.label().toLowerCase(Locale.ROOT)))
                .count();
        if (leakedLabels >= 2) {
            throw new AiProviderException("Hint rejected: reveals " + leakedLabels + " solution steps");
        }
        return clean;
    }

    public String validateAnswer(String text) {
        return validateText(text, MAX_ANSWER_CHARS);
    }

    public AiFeedback validateFeedback(String json) {
        AiFeedback feedback = parse(json, AiFeedback.class);
        if (feedback.summary() == null || feedback.summary().isBlank()) {
            throw new AiProviderException("Feedback without summary");
        }
        return new AiFeedback(validateText(feedback.summary(), MAX_ANSWER_CHARS),
                limit(feedback.strengths()), limit(feedback.improvements()), limit(feedback.missedEvidence()),
                limit(feedback.orderIssues()), limit(feedback.unnecessaryActions()), limit(feedback.nextSteps()));
    }

    public List<Recommendation> validateRecommendations(String json) {
        RecommendationList list = parse(json, RecommendationList.class);
        if (list.recommendations() == null || list.recommendations().isEmpty()) {
            throw new AiProviderException("No recommendations returned");
        }
        return list.recommendations().stream()
                .filter(r -> r.category() != null && r.topic() != null && !r.topic().isBlank())
                .limit(3)
                .map(r -> new Recommendation(validateText(r.topic(), 100), r.category(),
                        r.reason() == null ? "" : validateText(r.reason(), MAX_ITEM_CHARS)))
                .toList();
    }

    /** Only parsing here; the scenario rules are enforced by {@code ScenarioVariationService}. */
    public ScenarioDefinition parseScenarioDefinition(String json) {
        return parse(json, ScenarioDefinition.class);
    }

    /**
     * A translation may legitimately be longer than its source (Armenian runs longer than English), so the only
     * limit is an absolute one; a response far longer than the source means the model explained instead of
     * translating and is rejected.
     */
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

    private static List<String> limit(List<String> items) {
        return items.stream()
                .filter(i -> i != null && !i.isBlank())
                .map(i -> CONTROL_CHARS.matcher(i).replaceAll("").strip())
                .map(i -> i.length() > MAX_ITEM_CHARS ? i.substring(0, MAX_ITEM_CHARS) + "…" : i)
                .limit(MAX_LIST_ITEMS)
                .toList();
    }
}
