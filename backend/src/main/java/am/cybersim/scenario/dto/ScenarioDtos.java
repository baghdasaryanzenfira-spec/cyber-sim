package am.cybersim.scenario.dto;

import am.cybersim.scenario.ScenarioEnums.Category;
import am.cybersim.scenario.ScenarioEnums.Difficulty;

import java.time.Instant;
import java.util.List;

/**
 * Read models of scenarios. Student-facing DTOs deliberately contain no solution data
 * (no outcomes, points, explanations or recommended solution).
 */
public final class ScenarioDtos {

    private ScenarioDtos() {
    }

    /** Catalogue entry for students. */
    public record ScenarioSummary(Long id, String slug, String title, String summary, Difficulty difficulty,
                                  Category category, int estimatedMinutes) {
    }

    /** Briefing shown before a student starts the simulation. */
    public record ScenarioBriefing(Long id, String slug, String title, String summary, String description,
                                   Difficulty difficulty, Category category, int estimatedMinutes,
                                   List<String> learningObjectives, int resourceCount) {
    }

    /** Admin list entry. */
    public record AdminScenarioSummary(Long id, String slug, String title, Difficulty difficulty, Category category,
                                       boolean active, int version, Long sourceScenarioId, int actionCount,
                                       int eventCount, int maxScore, long attemptCount, Instant updatedAt) {
    }

    /** Admin editor view: metadata + the full editable definition. */
    public record AdminScenarioDetail(Long id, int version, boolean active, Long sourceScenarioId,
                                      Instant createdAt, Instant updatedAt, int maxScore,
                                      ScenarioDefinition definition) {
    }

    public record StatusUpdate(boolean active) {
    }
}
