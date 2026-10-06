package am.cybersim.simulation;

import am.cybersim.ai.dto.AiFeedback;
import am.cybersim.scoring.ScoreResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;

/**
 * Frozen result of a completed simulation (1:1). Breakdown and missed actions are JSON snapshots, so a later
 * change of scenario points never changes a historical grade.
 */
@Entity
@Table(name = "simulation_results")
public class SimulationResult {

    @Id
    @Column(name = "simulation_id")
    private Long simulationId;

    @Column(name = "raw_score", nullable = false)
    private int rawScore;

    @Column(name = "max_score", nullable = false)
    private int maxScore;

    @Column(name = "score_percent", nullable = false)
    private int scorePercent;

    @Column(name = "hint_penalty_total", nullable = false)
    private int hintPenaltyTotal;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<ScoreResult.ScoreItem> breakdown;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "missed_actions", nullable = false, columnDefinition = "jsonb")
    private List<ScoreResult.MissedAction> missedActions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private AiFeedback feedback;

    /** AI (real provider), MOCK (mock provider configured) or FALLBACK (real provider failed). */
    @Column(name = "feedback_source")
    private String feedbackSource;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SimulationResult() {
    }

    public SimulationResult(Long simulationId, ScoreResult score, Instant createdAt) {
        this.simulationId = simulationId;
        this.rawScore = score.rawScore();
        this.maxScore = score.maxScore();
        this.scorePercent = score.scorePercent();
        this.hintPenaltyTotal = score.hintPenaltyTotal();
        this.breakdown = score.items();
        this.missedActions = score.missedActions();
        this.createdAt = createdAt;
    }

    public void attachFeedback(AiFeedback feedback, String source) {
        this.feedback = feedback;
        this.feedbackSource = source;
    }

    public Long getSimulationId() {
        return simulationId;
    }

    public int getRawScore() {
        return rawScore;
    }

    public int getMaxScore() {
        return maxScore;
    }

    public int getScorePercent() {
        return scorePercent;
    }

    public int getHintPenaltyTotal() {
        return hintPenaltyTotal;
    }

    public List<ScoreResult.ScoreItem> getBreakdown() {
        return breakdown;
    }

    public List<ScoreResult.MissedAction> getMissedActions() {
        return missedActions;
    }

    public AiFeedback getFeedback() {
        return feedback;
    }

    public String getFeedbackSource() {
        return feedbackSource;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
