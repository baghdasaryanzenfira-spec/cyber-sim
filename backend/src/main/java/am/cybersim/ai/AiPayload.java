package am.cybersim.ai;

import am.cybersim.ai.context.SimulationSnapshot;
import am.cybersim.progress.ProgressStats;
import am.cybersim.scenario.dto.ScenarioDefinition;
import am.cybersim.scoring.ScoreResult;

/**
 * Typed input of every AI task. A sealed hierarchy lets providers use an exhaustive {@code switch}
 * (Java 21 pattern matching): adding a new task without handling it everywhere is a compile error.
 */
public sealed interface AiPayload {

    AiTask task();

    record Hint(SimulationSnapshot snapshot, int hintNumber) implements AiPayload {
        public AiTask task() {
            return AiTask.HINT;
        }
    }

    record Question(SimulationSnapshot snapshot, String question) implements AiPayload {
        public AiTask task() {
            return AiTask.QUESTION;
        }
    }

    record Feedback(SimulationSnapshot snapshot, ScoreResult score) implements AiPayload {
        public AiTask task() {
            return AiTask.FEEDBACK;
        }
    }

    record Recommendation(ProgressStats stats) implements AiPayload {
        public AiTask task() {
            return AiTask.RECOMMENDATION;
        }
    }

    record Variation(ScenarioDefinition original, String newSlug) implements AiPayload {
        public AiTask task() {
            return AiTask.VARIATION;
        }
    }

    /**
     * Translate one piece of displayed text into {@code languageName} on demand. The result is shown next to the
     * original and never stored: the English text stays the source of truth (ADR-12).
     */
    record Translation(String text, String languageName) implements AiPayload {
        public AiTask task() {
            return AiTask.TRANSLATION;
        }
    }
}
