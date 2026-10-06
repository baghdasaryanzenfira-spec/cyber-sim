package am.cybersim.ai;

import am.cybersim.ai.context.SimulationSnapshot;
import am.cybersim.ai.context.SimulationSnapshot.CatalogueAction;
import am.cybersim.ai.dto.AiDtos.Recommendation;
import am.cybersim.ai.dto.AiDtos.RecommendationList;
import am.cybersim.ai.dto.AiFeedback;
import am.cybersim.progress.ProgressStats;
import am.cybersim.scenario.ScenarioEnums.ActionCategory;
import am.cybersim.scenario.ScenarioEnums.ActionOutcome;
import am.cybersim.scenario.ScenarioEnums.Category;
import am.cybersim.scenario.dto.ScenarioDefinition;
import am.cybersim.scoring.ScoreResult;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Deterministic, rule-based "AI" used when no API key is configured and as the fallback when the real model
 * fails. It works on the same structured payloads as the real provider, so the rest of the platform cannot tell
 * the difference except through the {@code source} field.
 *
 * <p>Why rule-based instead of canned text: the mock must still be useful in a demonstration — hints follow the
 * student's actual progress and feedback reflects the actual actions.
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
            case AiPayload.Hint h -> hint(h.snapshot(), h.hintNumber());
            case AiPayload.Question q -> answer(q.snapshot(), q.question());
            case AiPayload.Feedback f -> toJson(feedback(f.snapshot(), f.score()));
            case AiPayload.Recommendation r -> toJson(recommendations(r.stats()));
            case AiPayload.Variation v -> toJson(variation(v.original(), v.newSlug()));
        };
        return new AiResponse(text, MODEL, null, null);
    }

    // ------------------------------------------------------------------ hints

    /**
     * Hint strategy: find the next expected action whose prerequisite is satisfied; hint 1 uses the author's
     * general hint, hint 2 points at the area, hint 3+ names the action (one action only).
     */
    String hint(SimulationSnapshot s, int hintNumber) {
        List<CatalogueAction> remaining = s.remainingExpected();
        if (remaining.isEmpty()) {
            return "You have covered all the important steps. Review the timeline once more and finish the "
                    + "simulation when you are confident in your response.";
        }
        CatalogueAction next = remaining.stream()
                .filter(a -> a.prerequisiteKey() == null || s.performed(a.prerequisiteKey()))
                .findFirst().orElse(remaining.getFirst());
        List<String> authored = s.scenario().hints();
        return switch (Math.min(hintNumber, 3)) {
            case 1 -> authored.isEmpty() ? directionFor(next) : authored.get(Math.min(progressIndex(s), authored.size() - 1));
            case 2 -> directionFor(next);
            default -> "A good next step would be: \"" + next.label() + "\". Think about why it matters before you do it.";
        };
    }

    /** Maps progress to the author's hint list: early hints for early phases, later hints for later phases. */
    private static int progressIndex(SimulationSnapshot s) {
        long expectedTotal = s.catalogue().stream().filter(a -> a.outcome() == ActionOutcome.EXPECTED).count();
        long done = expectedTotal - s.remainingExpected().size();
        return expectedTotal == 0 ? 0 : (int) (done * 4 / expectedTotal);
    }

    private static String directionFor(CatalogueAction next) {
        return switch (next.category()) {
            case INSPECT -> "You have not looked at every relevant data source yet. Which log would confirm or "
                    + "rule out what the alerts suggest?";
            case IDENTIFY -> "You have collected evidence — now decide what kind of incident this is and which "
                    + "resource is affected. Record your conclusion.";
            case CONTAIN -> "The incident is identified but still active. How can you stop the attacker without "
                    + "destroying evidence?";
            case ERADICATE -> "Containment is not enough: has the attacker left anything behind that would let "
                    + "them return?";
            case RECOVER -> "Think about everything the attacker may have learned or touched, and who needs to "
                    + "be informed.";
            case HARDEN -> "Finally, consider which configuration weakness made this incident possible and how "
                    + "to prevent it from happening again.";
        };
    }

    // ------------------------------------------------------------------ questions

    String answer(SimulationSnapshot s, String question) {
        String q = question.toLowerCase(Locale.ROOT);
        if (containsAny(q, "solution", "answer", "all steps", "what should i do", "ignore")) {
            return "I can't give you the full solution — the goal is to practise the investigation yourself. "
                    + "Use the hint button for a nudge, or ask me about a specific log entry or concept.";
        }
        Map<String, String> concepts = Map.ofEntries(
                Map.entry("brute", "A brute-force attack tries many passwords automatically. The typical sign is many "
                        + "failed logins from one source in a short time; it becomes an incident when a login succeeds."),
                Map.entry("isolat", "Isolating a resource (e.g. a quarantine security group) cuts the attacker off while "
                        + "keeping disk and memory available for forensic analysis — unlike deleting or terminating it."),
                Map.entry("mfa", "Multi-factor authentication requires a second factor besides the password, so a stolen "
                        + "or phished password alone is not enough to sign in."),
                Map.entry("session", "Changing a password does not end sessions or tokens that already exist. Revoke "
                        + "active sessions so an attacker who is already signed in is logged out."),
                Map.entry("access key", "Access keys are long-lived credentials for programmatic access. They are "
                        + "independent of the password, so they must be deactivated separately."),
                Map.entry("public", "A storage bucket is public when its policy or ACL grants access to everyone "
                        + "(Principal: *). Public access blocks are a safety net that overrides such grants."),
                Map.entry("flow log", "Flow logs record network connections (source, destination, port, bytes). They "
                        + "help confirm attacks and spot unusual outbound traffic such as command-and-control."),
                Map.entry("evidence", "Evidence is any log entry or alert that supports a conclusion about the incident. "
                        + "Use the flag button on a log entry to add it to your evidence board."));
        Optional<String> concept = concepts.entrySet().stream()
                .filter(e -> q.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst();
        if (concept.isPresent()) {
            return concept.get();
        }
        long alerts = s.visibleEvents().stream().filter(e -> "ALERT".equals(e.type())).count();
        return "Good question. In this simulation you currently see " + s.visibleEvents().size() + " log entries and "
                + alerts + " alert(s). Start from the high-severity alerts, identify the affected resource, and look "
                + "for the log source that can confirm what happened. (Offline assistant mode — answers are limited.)";
    }

    private static boolean containsAny(String text, String... words) {
        for (String w : words) {
            if (text.contains(w)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ feedback

    AiFeedback feedback(SimulationSnapshot s, ScoreResult score) {
        List<String> strengths = s.performedActions().stream()
                .filter(p -> !p.duplicate() && p.outcome() == ActionOutcome.EXPECTED)
                .map(p -> "You performed \"" + p.label() + "\".")
                .limit(5).toList();
        List<String> improvements = new ArrayList<>();
        s.performedActions().stream()
                .filter(p -> p.outcome() == ActionOutcome.HARMFUL)
                .forEach(p -> improvements.add("Avoid \"" + p.label() + "\": " + explanationOf(s, p.actionKey())));
        score.missedActions().forEach(m -> improvements.add("You missed \"" + m.label() + "\": " + m.explanation()));
        List<String> missedEvidence = s.evidence().stream()
                .filter(e -> !e.flagged())
                .map(e -> (e.revealed() ? "Not flagged: " : "Never uncovered: ") + e.message())
                .limit(5).toList();
        List<String> orderIssues = s.performedActions().stream()
                .filter(SimulationSnapshot.PerformedView::outOfOrder)
                .map(p -> "\"" + p.label() + "\" was done before \"" + prerequisiteLabel(s, p.actionKey()) + "\".")
                .toList();
        List<String> unnecessary = s.performedActions().stream()
                .filter(p -> p.outcome() == ActionOutcome.NEUTRAL || p.duplicate())
                .map(p -> "\"" + p.label() + "\"" + (p.duplicate() ? " was repeated." : " was not needed for this incident."))
                .distinct().toList();
        String verdict = score.scorePercent() >= 85 ? "Excellent work" : score.scorePercent() >= 60 ? "Good work"
                : "A solid start";
        String summary = verdict + " — you scored " + score.scorePercent() + "/100. You completed "
                + strengths.size() + " of " + (strengths.size() + score.missedActions().size())
                + " key response steps" + (improvements.isEmpty() ? "." : "; review the improvements below.");
        List<String> nextSteps = score.missedActions().stream()
                .map(m -> m.category())
                .distinct()
                .map(c -> "Practise " + c.name().toLowerCase(Locale.ROOT) + " steps of the incident-response process.")
                .limit(3).toList();
        return new AiFeedback(summary, strengths, improvements.stream().limit(5).toList(), missedEvidence,
                orderIssues, unnecessary, nextSteps);
    }

    private static String explanationOf(SimulationSnapshot s, String key) {
        return s.catalogue().stream().filter(a -> a.key().equals(key)).map(CatalogueAction::explanation)
                .findFirst().orElse("");
    }

    private static String prerequisiteLabel(SimulationSnapshot s, String key) {
        return s.catalogue().stream().filter(a -> a.key().equals(key)).map(CatalogueAction::prerequisiteKey)
                .findFirst()
                .flatMap(pre -> s.catalogue().stream().filter(a -> a.key().equals(pre)).map(CatalogueAction::label).findFirst())
                .orElse("its prerequisite");
    }

    // ------------------------------------------------------------------ recommendations

    RecommendationList recommendations(ProgressStats stats) {
        List<Recommendation> result = new ArrayList<>();
        stats.categories().stream()
                .filter(c -> c.averageScore() != null && c.averageScore() < 80)
                .sorted(Comparator.comparing(ProgressStats.CategoryStat::averageScore))
                .limit(2)
                .forEach(c -> result.add(new Recommendation(topicFor(c.category()), c.category(),
                        "Your average score in this area is " + c.averageScore() + "/100.")));
        stats.missedActionsByCategory().entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .filter(e -> e.getValue() > 0 && result.size() < 3)
                .ifPresent(e -> result.add(new Recommendation(topicFor(e.getKey()), Category.INCIDENT_RESPONSE,
                        "You most often missed " + e.getKey().name().toLowerCase(Locale.ROOT) + " steps ("
                                + e.getValue() + " times).")));
        if (result.size() < 3) {
            stats.categories().stream()
                    .filter(c -> c.completed() == 0)
                    .limit(3 - result.size())
                    .forEach(c -> result.add(new Recommendation(topicFor(c.category()), c.category(),
                            "You have not completed a scenario in this area yet.")));
        }
        if (result.isEmpty()) {
            result.add(new Recommendation("Incident response", Category.INCIDENT_RESPONSE,
                    "Keep practising: repeat scenarios and try to reach 100/100 without hints."));
        }
        return new RecommendationList(result);
    }

    static String topicFor(Category category) {
        return switch (category) {
            case AUTHENTICATION -> "Authentication security";
            case IAM -> "IAM security and least privilege";
            case NETWORK -> "Network security";
            case STORAGE -> "Cloud storage and data protection";
            case LOGGING -> "Cloud logging and monitoring";
            case INCIDENT_RESPONSE -> "Incident response";
        };
    }

    private static String topicFor(ActionCategory category) {
        return switch (category) {
            case INSPECT -> "Cloud logging and investigation";
            case IDENTIFY -> "Incident analysis and classification";
            case CONTAIN -> "Incident containment";
            case ERADICATE -> "Eradication of attacker persistence";
            case RECOVER -> "Recovery and breach notification";
            case HARDEN -> "Security hardening";
        };
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
                copy.recommendedSolution(), copy.hintPenalty(), copy.outOfOrderPenalty(), false,
                copy.learningObjectives(), copy.resources(), copy.events(), copy.actions(), copy.hints());
    }

    private String toJson(Object value) {
        return objectMapper.writeValueAsString(value);
    }
}
