package am.cybersim.analytics;

import am.cybersim.analytics.AnalyticsDtos.AiUsage;
import am.cybersim.analytics.AnalyticsDtos.Bucket;
import am.cybersim.analytics.AnalyticsDtos.DayCount;
import am.cybersim.analytics.AnalyticsDtos.MistakeStat;
import am.cybersim.analytics.AnalyticsDtos.Mistakes;
import am.cybersim.analytics.AnalyticsDtos.Overview;
import am.cybersim.analytics.AnalyticsDtos.ScenarioStat;
import am.cybersim.analytics.AnalyticsDtos.Totals;
import am.cybersim.analytics.AnalyticsDtos.UserStats;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Read-only reporting for administrators (requirement FR-22).
 *
 * <p>Why plain SQL ({@link JdbcClient}) instead of JPA here: reporting queries aggregate across many tables and use
 * PostgreSQL features such as {@code FILTER}, {@code date_trunc} and {@code jsonb_array_elements}. Expressing them in
 * SQL is shorter, faster and easier to explain than loading entities and aggregating in Java.
 */
@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    private final JdbcClient jdbc;
    private final Clock clock;

    public AnalyticsService(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public Overview overview() {
        return new Overview(totals(), scenarioStats(), scoreDistribution(), attemptsPerDay(14), aiUsage());
    }

    Totals totals() {
        return jdbc.sql("""
                        SELECT (SELECT COUNT(*) FROM users)                                   AS users,
                               (SELECT COUNT(*) FROM users WHERE role = 'STUDENT')            AS students,
                               (SELECT COUNT(*) FROM scenarios WHERE active)                  AS active_scenarios,
                               (SELECT COUNT(*) FROM simulations)                             AS simulations,
                               (SELECT COUNT(*) FROM simulations WHERE status = 'COMPLETED')  AS completed,
                               (SELECT COUNT(*) FROM simulations
                                 WHERE status IN ('RUNNING','INVESTIGATING','RESPONDING'))    AS in_progress,
                               (SELECT CAST(ROUND(AVG(score_percent)) AS int) FROM simulation_results) AS avg_score
                        """)
                .query((rs, n) -> {
                    long sims = rs.getLong("simulations");
                    long completed = rs.getLong("completed");
                    return new Totals(rs.getLong("users"), rs.getLong("students"), rs.getLong("active_scenarios"),
                            sims, completed, rs.getLong("in_progress"), rs.getObject("avg_score", Integer.class),
                            sims == 0 ? null : (int) Math.round(completed * 100.0 / sims));
                })
                .single();
    }

    List<ScenarioStat> scenarioStats() {
        return jdbc.sql("""
                        SELECT sc.id, sc.title,
                               COUNT(s.id)                                         AS attempts,
                               COUNT(s.id) FILTER (WHERE s.status = 'COMPLETED')   AS completed,
                               CAST(ROUND(AVG(r.score_percent)) AS int)            AS avg_score,
                               CAST(ROUND(AVG(EXTRACT(EPOCH FROM (s.completed_at - s.started_at)) / 60)
                                     FILTER (WHERE s.status = 'COMPLETED')) AS int) AS avg_minutes
                          FROM scenarios sc
                          LEFT JOIN simulations s        ON s.scenario_id = sc.id
                          LEFT JOIN simulation_results r ON r.simulation_id = s.id
                         GROUP BY sc.id, sc.title
                         ORDER BY attempts DESC, sc.title
                        """)
                .query((rs, n) -> new ScenarioStat(rs.getLong("id"), rs.getString("title"), rs.getLong("attempts"),
                        rs.getLong("completed"), rs.getObject("avg_score", Integer.class),
                        rs.getObject("avg_minutes", Integer.class)))
                .list();
    }

    List<Bucket> scoreDistribution() {
        Map<Integer, Long> counts = jdbc.sql("""
                        SELECT LEAST(score_percent / 20, 4) AS bucket, COUNT(*) AS cnt
                          FROM simulation_results GROUP BY bucket
                        """)
                .query((rs, n) -> Map.entry(rs.getInt("bucket"), rs.getLong("cnt")))
                .list().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        String[] labels = {"0-19", "20-39", "40-59", "60-79", "80-100"};
        return java.util.stream.IntStream.range(0, labels.length)
                .mapToObj(i -> new Bucket(labels[i], counts.getOrDefault(i, 0L)))
                .toList();
    }

    List<DayCount> attemptsPerDay(int days) {
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        LocalDate from = today.minusDays(days - 1L);
        Map<LocalDate, DayCount> byDay = jdbc.sql("""
                        SELECT CAST(created_at AT TIME ZONE 'UTC' AS date) AS day,
                               COUNT(*) AS attempts,
                               COUNT(*) FILTER (WHERE status = 'COMPLETED') AS completed
                          FROM simulations
                         WHERE created_at >= :from
                         GROUP BY day
                        """)
                .param("from", Date.valueOf(from))
                .query((rs, n) -> new DayCount(rs.getDate("day").toLocalDate(), rs.getLong("attempts"),
                        rs.getLong("completed")))
                .list().stream()
                .collect(Collectors.toMap(DayCount::day, d -> d));
        return from.datesUntil(today.plusDays(1))
                .map(d -> byDay.getOrDefault(d, new DayCount(d, 0, 0)))
                .toList();
    }

    List<AiUsage> aiUsage() {
        return jdbc.sql("""
                        SELECT interaction_type, status, COUNT(*) AS cnt, CAST(ROUND(AVG(latency_ms)) AS int) AS avg_latency
                          FROM ai_interactions GROUP BY interaction_type, status ORDER BY interaction_type, status
                        """)
                .query((rs, n) -> new AiUsage(rs.getString("interaction_type"), rs.getString("status"),
                        rs.getLong("cnt"), rs.getObject("avg_latency", Integer.class)))
                .list();
    }

    /** Most common mistakes across all students (requirement: "view common user mistakes"). */
    public Mistakes mistakes() {
        return new Mistakes(actionsWithOutcome("HARMFUL"), missedActions(), actionsWithOutcome("NEUTRAL"));
    }

    private List<MistakeStat> actionsWithOutcome(String outcome) {
        return jdbc.sql("""
                        WITH completed AS (
                            SELECT scenario_id, COUNT(*) AS n FROM simulations WHERE status = 'COMPLETED' GROUP BY scenario_id)
                        SELECT sc.title, a.action_key, a.label, COUNT(*) AS cnt,
                               CAST(ROUND(100.0 * COUNT(*) / NULLIF(MAX(c.n), 0)) AS int) AS pct
                          FROM simulation_actions a
                          JOIN simulations s ON s.id = a.simulation_id
                          JOIN scenarios sc  ON sc.id = s.scenario_id
                          LEFT JOIN completed c ON c.scenario_id = s.scenario_id
                         WHERE a.outcome = :outcome AND a.result = 'APPLIED'
                         GROUP BY sc.title, a.action_key, a.label
                         ORDER BY cnt DESC, sc.title
                         LIMIT 10
                        """)
                .param("outcome", outcome)
                .query((rs, n) -> new MistakeStat(rs.getString("title"), rs.getString("action_key"),
                        rs.getString("label"), rs.getLong("cnt"), rs.getObject("pct", Integer.class)))
                .list();
    }

    /** Expected actions that were missed, read from the frozen {@code missed_actions} jsonb snapshots. */
    private List<MistakeStat> missedActions() {
        return jdbc.sql("""
                        WITH completed AS (
                            SELECT scenario_id, COUNT(*) AS n FROM simulations WHERE status = 'COMPLETED' GROUP BY scenario_id)
                        SELECT sc.title, m ->> 'actionKey' AS action_key, m ->> 'label' AS label, COUNT(*) AS cnt,
                               CAST(ROUND(100.0 * COUNT(*) / NULLIF(MAX(c.n), 0)) AS int) AS pct
                          FROM simulation_results r
                          JOIN simulations s ON s.id = r.simulation_id
                          JOIN scenarios sc  ON sc.id = s.scenario_id
                          LEFT JOIN completed c ON c.scenario_id = s.scenario_id
                         CROSS JOIN LATERAL jsonb_array_elements(r.missed_actions) AS m
                         GROUP BY sc.title, m ->> 'actionKey', m ->> 'label'
                         ORDER BY cnt DESC, sc.title
                         LIMIT 10
                        """)
                .query((rs, n) -> new MistakeStat(rs.getString("title"), rs.getString("action_key"),
                        rs.getString("label"), rs.getLong("cnt"), rs.getObject("pct", Integer.class)))
                .list();
    }

    /** Attempt statistics per user id, for the admin user list. */
    public Map<Long, UserStats> userStats() {
        return jdbc.sql("""
                        SELECT s.user_id, COUNT(*) AS attempts,
                               COUNT(*) FILTER (WHERE s.status = 'COMPLETED') AS completed,
                               CAST(ROUND(AVG(r.score_percent)) AS int) AS avg_score
                          FROM simulations s
                          LEFT JOIN simulation_results r ON r.simulation_id = s.id
                         GROUP BY s.user_id
                        """)
                .query((rs, n) -> Map.entry(rs.getLong("user_id"), new UserStats(rs.getLong("attempts"),
                        rs.getLong("completed"), rs.getObject("avg_score", Integer.class))))
                .list().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
