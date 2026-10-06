package am.cybersim.progress;

import am.cybersim.ai.AiGateway.AiResult;
import am.cybersim.ai.TutorService;
import am.cybersim.ai.dto.AiDtos.Recommendation;
import am.cybersim.ai.dto.AiDtos.RecommendationsView;
import am.cybersim.progress.ProgressDtos.ProgressView;
import am.cybersim.progress.ProgressDtos.ScenarioProgress;
import am.cybersim.progress.ProgressDtos.ScorePoint;
import am.cybersim.progress.ProgressDtos.Totals;
import am.cybersim.progress.ProgressStats.CategoryStat;
import am.cybersim.scenario.ScenarioEnums.ActionCategory;
import am.cybersim.scenario.ScenarioEnums.Category;
import am.cybersim.scenario.ScenarioRepository;
import am.cybersim.scoring.ScoreResult;
import am.cybersim.simulation.Simulation;
import am.cybersim.simulation.SimulationMapper;
import am.cybersim.simulation.SimulationRepository;
import am.cybersim.simulation.SimulationResult;
import am.cybersim.simulation.SimulationResultRepository;
import am.cybersim.simulation.SimulationStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Learning progress of a student (requirement FR-21). Computed on demand from simulations and their frozen
 * results instead of a separate denormalised "progress" table — the data volume is small and a single source
 * of truth avoids inconsistencies (see 05-database-design).
 */
@Service
public class ProgressService {

    private final SimulationRepository simulations;
    private final SimulationResultRepository results;
    private final ScenarioRepository scenarios;
    private final SimulationMapper simulationMapper;
    private final TutorService tutor;
    private final TransactionTemplate tx;

    public ProgressService(SimulationRepository simulations, SimulationResultRepository results,
                           ScenarioRepository scenarios, SimulationMapper simulationMapper, TutorService tutor,
                           PlatformTransactionManager transactionManager) {
        this.simulations = simulations;
        this.results = results;
        this.scenarios = scenarios;
        this.simulationMapper = simulationMapper;
        this.tutor = tutor;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setReadOnly(true);
    }

    @Transactional(readOnly = true)
    public ProgressView progress(Long userId) {
        List<Simulation> list = simulations.findByUserIdWithScenario(userId);
        Map<Long, SimulationResult> resultBySim = resultsFor(list);

        List<Integer> scores = resultBySim.values().stream().map(SimulationResult::getScorePercent).toList();
        long minutes = list.stream()
                .filter(s -> s.getStartedAt() != null && s.getCompletedAt() != null)
                .mapToLong(s -> Duration.between(s.getStartedAt(), s.getCompletedAt()).toMinutes())
                .sum();
        Totals totals = new Totals(list.size(), count(list, SimulationStatus.COMPLETED),
                count(list, SimulationStatus.ABANDONED), average(scores),
                scores.stream().max(Integer::compare).orElse(null),
                list.stream().mapToInt(Simulation::getHintsUsed).sum(), minutes);

        List<ScorePoint> history = list.stream()
                .filter(s -> resultBySim.containsKey(s.getId()))
                .sorted(Comparator.comparing(Simulation::getCompletedAt))
                .map(s -> new ScorePoint(s.getId(), s.getScenario().getTitle(),
                        resultBySim.get(s.getId()).getScorePercent(), s.getCompletedAt()))
                .toList();

        return new ProgressView(totals, categoryStats(list, resultBySim), scenarioProgress(list, resultBySim),
                history, list.stream().limit(5)
                .map(s -> simulationMapper.toSummary(s, scoreOf(resultBySim, s))).toList());
    }

    /** Statistics used as AI input for learning recommendations. */
    @Transactional(readOnly = true)
    public ProgressStats stats(Long userId) {
        List<Simulation> list = simulations.findByUserIdWithScenario(userId);
        Map<Long, SimulationResult> resultBySim = resultsFor(list);
        Map<ActionCategory, Integer> missed = new EnumMap<>(ActionCategory.class);
        int harmful = 0;
        for (SimulationResult r : resultBySim.values()) {
            for (ScoreResult.MissedAction m : r.getMissedActions()) {
                if (m.category() != null) {
                    missed.merge(m.category(), 1, Integer::sum);
                }
            }
            harmful += (int) r.getBreakdown().stream().filter(i -> i.kind() == ScoreResult.ItemKind.HARMFUL).count();
        }
        return new ProgressStats(resultBySim.size(), average(resultBySim.values().stream()
                .map(SimulationResult::getScorePercent).toList()),
                list.stream().mapToInt(Simulation::getHintsUsed).sum(), harmful,
                categoryStats(list, resultBySim), missed);
    }

    /** D. AI learning recommendations; the AI call happens outside any transaction. */
    public RecommendationsView recommendations(Long userId) {
        ProgressStats stats = tx.execute(status -> stats(userId));
        AiResult<List<Recommendation>> result = tutor.recommendations(userId, Objects.requireNonNull(stats));
        return new RecommendationsView(result.value(), result.source());
    }

    private List<CategoryStat> categoryStats(List<Simulation> list, Map<Long, SimulationResult> resultBySim) {
        Set<Category> categories = new LinkedHashSet<>();
        scenarios.findByActiveTrueOrderByDifficultyAscTitleAsc().forEach(s -> categories.add(s.getCategory()));
        list.forEach(s -> categories.add(s.getScenario().getCategory()));
        List<CategoryStat> stats = new ArrayList<>();
        for (Category category : categories) {
            List<Simulation> inCategory = list.stream().filter(s -> s.getScenario().getCategory() == category).toList();
            List<Integer> scores = inCategory.stream().map(s -> scoreOf(resultBySim, s)).filter(Objects::nonNull).toList();
            stats.add(new CategoryStat(category, inCategory.size(), scores.size(), average(scores),
                    scores.stream().max(Integer::compare).orElse(null)));
        }
        return stats;
    }

    private List<ScenarioProgress> scenarioProgress(List<Simulation> list, Map<Long, SimulationResult> resultBySim) {
        return scenarios.findByActiveTrueOrderByDifficultyAscTitleAsc().stream()
                .map(sc -> {
                    List<Simulation> mine = list.stream().filter(s -> s.getScenario().getId().equals(sc.getId())).toList();
                    Integer best = mine.stream().map(s -> scoreOf(resultBySim, s)).filter(Objects::nonNull)
                            .max(Integer::compare).orElse(null);
                    Long active = mine.stream().filter(s -> !s.getStatus().isTerminal()).map(Simulation::getId)
                            .findFirst().orElse(null);
                    return new ScenarioProgress(sc.getId(), sc.getTitle(), sc.getDifficulty(), sc.getCategory(),
                            mine.size(), best, mine.isEmpty() ? null : mine.getFirst().getStatus(), active);
                })
                .toList();
    }

    private Map<Long, SimulationResult> resultsFor(List<Simulation> list) {
        return results.findBySimulationIdIn(list.stream().map(Simulation::getId).toList()).stream()
                .collect(Collectors.toMap(SimulationResult::getSimulationId, Function.identity()));
    }

    private static Integer scoreOf(Map<Long, SimulationResult> resultBySim, Simulation s) {
        SimulationResult r = resultBySim.get(s.getId());
        return r == null ? null : r.getScorePercent();
    }

    private static int count(List<Simulation> list, SimulationStatus status) {
        return (int) list.stream().filter(s -> s.getStatus() == status).count();
    }

    static Integer average(List<Integer> values) {
        return values.isEmpty() ? null
                : (int) Math.round(values.stream().mapToInt(Integer::intValue).average().orElse(0));
    }
}
