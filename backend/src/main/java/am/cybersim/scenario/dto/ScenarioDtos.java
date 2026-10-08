package am.cybersim.scenario.dto;

import am.cybersim.scenario.ScenarioEnums.Category;
import am.cybersim.scenario.ScenarioEnums.Difficulty;
import am.cybersim.scenario.ScenarioEnums.ScenarioStatus;

import java.time.Instant;

/** Read models of the scenario authoring API. */
public final class ScenarioDtos {

    private ScenarioDtos() {
    }

    /** List entry. {@code publishedVersion} is null for scenarios that were never published. */
    public record AdminScenarioSummary(Long id, String slug, String title, Difficulty difficulty, Category category,
                                       ScenarioStatus status, int revision, Integer publishedVersion,
                                       Long sourceScenarioId, int actionCount, int eventCount, int maxScore,
                                       Instant updatedAt) {
    }

    /** Editor view: metadata + the full editable definition. */
    public record AdminScenarioDetail(Long id, ScenarioStatus status, int revision, Integer publishedVersion,
                                      Long sourceScenarioId, Instant createdAt, Instant updatedAt, int maxScore,
                                      ScenarioDefinition definition) {
    }
}
