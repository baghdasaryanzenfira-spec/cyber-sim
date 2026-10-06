package am.cybersim.simulation;

import am.cybersim.scenario.ScenarioAction;
import am.cybersim.scenario.ScenarioEnums.ActionCategory;
import am.cybersim.scenario.ScenarioEnums.ActionOutcome;
import am.cybersim.scenario.ScenarioEnums.ActionPhase;
import am.cybersim.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An action performed by a student (requirement FR-13: timestamp, simulation, user, action type,
 * target resource, metadata, result). Label, outcome and points are snapshots taken at the time of the action,
 * so later scenario edits do not change history.
 */
@Entity
@Table(name = "simulation_actions")
public class SimulationAction {

    public enum Result { APPLIED, DUPLICATE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "simulation_id")
    private Simulation simulation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private int sequence;

    @Column(name = "action_key", nullable = false)
    private String actionKey;

    @Column(nullable = false)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActionPhase phase;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActionCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActionOutcome outcome;

    @Column(name = "target_resource_key")
    private String targetResourceKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Result result;

    @Column(name = "points_awarded", nullable = false)
    private int pointsAwarded;

    @Column(name = "out_of_order", nullable = false)
    private boolean outOfOrder;

    @Column(name = "result_message", nullable = false, columnDefinition = "text")
    private String resultMessage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata = new LinkedHashMap<>();

    @Column(name = "performed_at", nullable = false)
    private Instant performedAt;

    protected SimulationAction() {
    }

    SimulationAction(User user, int sequence, ScenarioAction definition, Result result, int pointsAwarded,
                     boolean outOfOrder, String resultMessage, Map<String, Object> metadata, Instant performedAt) {
        this.user = user;
        this.sequence = sequence;
        this.actionKey = definition.getActionKey();
        this.label = definition.getLabel();
        this.phase = definition.getPhase();
        this.category = definition.getCategory();
        this.outcome = definition.getOutcome();
        this.targetResourceKey = definition.getTargetResourceKey();
        this.result = result;
        this.pointsAwarded = pointsAwarded;
        this.outOfOrder = outOfOrder;
        this.resultMessage = resultMessage;
        this.metadata = new LinkedHashMap<>(metadata);
        this.performedAt = performedAt;
    }

    void attachTo(Simulation simulation) {
        this.simulation = simulation;
    }

    public boolean isDuplicate() {
        return result == Result.DUPLICATE;
    }

    public Long getId() {
        return id;
    }

    public int getSequence() {
        return sequence;
    }

    public String getActionKey() {
        return actionKey;
    }

    public String getLabel() {
        return label;
    }

    public ActionPhase getPhase() {
        return phase;
    }

    public ActionCategory getCategory() {
        return category;
    }

    public ActionOutcome getOutcome() {
        return outcome;
    }

    public String getTargetResourceKey() {
        return targetResourceKey;
    }

    public Result getResult() {
        return result;
    }

    public int getPointsAwarded() {
        return pointsAwarded;
    }

    public boolean isOutOfOrder() {
        return outOfOrder;
    }

    public String getResultMessage() {
        return resultMessage;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public Instant getPerformedAt() {
        return performedAt;
    }
}
