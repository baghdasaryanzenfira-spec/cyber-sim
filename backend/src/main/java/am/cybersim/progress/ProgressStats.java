package am.cybersim.progress;

import am.cybersim.scenario.ScenarioEnums.ActionCategory;
import am.cybersim.scenario.ScenarioEnums.Category;

import java.util.List;
import java.util.Map;

/**
 * Aggregated learning statistics of one student; input for learning recommendations (requirement §8.D).
 *
 * @param missedActionsByCategory how often expected actions of each incident-response category were missed
 */
public record ProgressStats(
        int completedSimulations,
        Integer averageScore,
        int hintsUsed,
        int harmfulActions,
        List<CategoryStat> categories,
        Map<ActionCategory, Integer> missedActionsByCategory) {

    public record CategoryStat(Category category, int attempts, int completed, Integer averageScore,
                               Integer bestScore) {
    }
}
