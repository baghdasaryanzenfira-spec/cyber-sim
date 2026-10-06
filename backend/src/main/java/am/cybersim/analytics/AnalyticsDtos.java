package am.cybersim.analytics;

import java.time.LocalDate;
import java.util.List;

public final class AnalyticsDtos {

    private AnalyticsDtos() {
    }

    public record Overview(Totals totals, List<ScenarioStat> scenarios, List<Bucket> scoreDistribution,
                           List<DayCount> attemptsPerDay, List<AiUsage> aiUsage) {
    }

    public record Totals(long users, long students, long activeScenarios, long simulations, long completed,
                         long inProgress, Integer averageScore, Integer completionRatePercent) {
    }

    public record ScenarioStat(Long scenarioId, String title, long attempts, long completed, Integer averageScore,
                               Integer averageDurationMinutes) {
    }

    public record Bucket(String range, long count) {
    }

    public record DayCount(LocalDate day, long attempts, long completed) {
    }

    public record AiUsage(String type, String status, long count, Integer averageLatencyMs) {
    }

    public record Mistakes(List<MistakeStat> harmfulActions, List<MistakeStat> missedActions,
                           List<MistakeStat> unnecessaryActions) {
    }

    /** @param percent share of completed attempts of that scenario in which the mistake occurred */
    public record MistakeStat(String scenarioTitle, String actionKey, String label, long count, Integer percent) {
    }

    public record UserStats(long attempts, long completed, Integer averageScore) {
    }
}
