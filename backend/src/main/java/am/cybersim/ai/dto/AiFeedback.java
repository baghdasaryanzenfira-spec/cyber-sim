package am.cybersim.ai.dto;

import java.util.List;

/**
 * Structured post-simulation feedback (requirement §8.B). Produced by the AI provider as JSON and validated
 * by {@code AiOutputValidator}, or produced deterministically by the mock provider.
 */
public record AiFeedback(
        String summary,
        List<String> strengths,
        List<String> improvements,
        List<String> missedEvidence,
        List<String> orderIssues,
        List<String> unnecessaryActions,
        List<String> nextSteps) {

    public AiFeedback {
        strengths = nullToEmpty(strengths);
        improvements = nullToEmpty(improvements);
        missedEvidence = nullToEmpty(missedEvidence);
        orderIssues = nullToEmpty(orderIssues);
        unnecessaryActions = nullToEmpty(unnecessaryActions);
        nextSteps = nullToEmpty(nextSteps);
    }

    private static List<String> nullToEmpty(List<String> list) {
        return list == null ? List.of() : List.copyOf(list);
    }
}
