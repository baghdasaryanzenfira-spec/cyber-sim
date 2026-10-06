package am.cybersim.scenario;

import am.cybersim.scenario.ScenarioEnums.ActionCategory;
import am.cybersim.scenario.ScenarioEnums.ActionOutcome;
import am.cybersim.scenario.ScenarioEnums.ActionPhase;
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

/**
 * An action the student may choose during the simulation, together with its scoring rule
 * ({@code outcome}, {@code points}, {@code prerequisiteActionKey}) and its effect on the simulated environment
 * ({@code effectStatus} on {@code targetResourceKey}, revealed events).
 */
@Entity
@Table(name = "scenario_actions")
public class ScenarioAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scenario_id")
    private Scenario scenario;

    @Column(nullable = false)
    private int position;

    @Column(name = "action_key", nullable = false)
    private String actionKey;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActionPhase phase;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActionCategory category;

    @Column(name = "target_resource_key")
    private String targetResourceKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActionOutcome outcome;

    @Column(nullable = false)
    private int points;

    @Column(name = "prerequisite_action_key")
    private String prerequisiteActionKey;

    @Column(name = "effect_status")
    private String effectStatus;

    @Column(name = "result_message", nullable = false, columnDefinition = "text")
    private String resultMessage;

    @Column(nullable = false, columnDefinition = "text")
    private String explanation;

    protected ScenarioAction() {
    }

    public ScenarioAction(int position, String actionKey, String label, String description, ActionPhase phase,
                          ActionCategory category, String targetResourceKey, ActionOutcome outcome, int points,
                          String prerequisiteActionKey, String effectStatus, String resultMessage,
                          String explanation) {
        this.position = position;
        this.actionKey = actionKey;
        this.label = label;
        this.description = description;
        this.phase = phase;
        this.category = category;
        this.targetResourceKey = targetResourceKey;
        this.outcome = outcome;
        this.points = points;
        this.prerequisiteActionKey = prerequisiteActionKey;
        this.effectStatus = effectStatus;
        this.resultMessage = resultMessage;
        this.explanation = explanation;
    }

    void attachTo(Scenario scenario) {
        this.scenario = scenario;
    }

    public int getPosition() {
        return position;
    }

    public String getActionKey() {
        return actionKey;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public ActionPhase getPhase() {
        return phase;
    }

    public ActionCategory getCategory() {
        return category;
    }

    public String getTargetResourceKey() {
        return targetResourceKey;
    }

    public ActionOutcome getOutcome() {
        return outcome;
    }

    public int getPoints() {
        return points;
    }

    public String getPrerequisiteActionKey() {
        return prerequisiteActionKey;
    }

    public String getEffectStatus() {
        return effectStatus;
    }

    public String getResultMessage() {
        return resultMessage;
    }

    public String getExplanation() {
        return explanation;
    }
}
