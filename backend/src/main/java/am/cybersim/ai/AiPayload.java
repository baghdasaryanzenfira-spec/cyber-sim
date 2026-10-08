package am.cybersim.ai;

import am.cybersim.ai.dto.ReviewSubject;
import am.cybersim.scenario.dto.ScenarioDefinition;

/**
 * Typed input of every AI task. A sealed hierarchy lets providers use an exhaustive {@code switch}
 * (Java 21 pattern matching): adding a new task without handling it everywhere is a compile error.
 */
public sealed interface AiPayload {

    AiTask task();

    record Variation(ScenarioDefinition original, String newSlug) implements AiPayload {
        public AiTask task() {
            return AiTask.VARIATION;
        }
    }

    /**
     * Polish the narrative of a freshly generated scenario draft for the admin's brief. The structure (keys,
     * phases, outcomes, points, references) must stay identical.
     */
    record Generation(ScenarioDefinition draft, String brief) implements AiPayload {
        public AiTask task() {
            return AiTask.GENERATION;
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

    /**
     * Review one submitted exam attempt against the scenario it was played on (ADR-13). The subject carries only
     * platform-produced facts (replayed steps, deterministic score, missed actions) — never learner free text.
     */
    record Review(ScenarioDefinition definition, ReviewSubject subject) implements AiPayload {
        public AiTask task() {
            return AiTask.REVIEW;
        }
    }
}
