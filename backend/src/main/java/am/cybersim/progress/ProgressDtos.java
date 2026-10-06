package am.cybersim.progress;

import am.cybersim.scenario.ScenarioEnums.Category;
import am.cybersim.scenario.ScenarioEnums.Difficulty;
import am.cybersim.simulation.SimulationStatus;
import am.cybersim.simulation.dto.SimulationDtos.SimulationSummary;

import java.time.Instant;
import java.util.List;

public final class ProgressDtos {

    private ProgressDtos() {
    }

    public record ProgressView(Totals totals, List<ProgressStats.CategoryStat> categories,
                               List<ScenarioProgress> scenarios, List<ScorePoint> scoreHistory,
                               List<SimulationSummary> recentAttempts) {
    }

    public record Totals(int attempts, int completed, int abandoned, Integer averageScore, Integer bestScore,
                         int hintsUsed, long trainingMinutes) {
    }

    public record ScenarioProgress(Long scenarioId, String title, Difficulty difficulty, Category category,
                                   int attempts, Integer bestScore, SimulationStatus lastStatus,
                                   Long activeSimulationId) {
    }

    public record ScorePoint(Long simulationId, String scenarioTitle, int scorePercent, Instant completedAt) {
    }
}
