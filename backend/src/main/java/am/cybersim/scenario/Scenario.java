package am.cybersim.scenario;

import am.cybersim.scenario.ScenarioEnums.Category;
import am.cybersim.scenario.ScenarioEnums.Difficulty;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Scenario template (aggregate root). Owns its objectives, resources, events, actions and hints;
 * children are always created/replaced together through {@link ScenarioService}.
 */
@Entity
@Table(name = "scenarios")
public class Scenario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String summary;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Difficulty difficulty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;

    @Column(name = "estimated_minutes", nullable = false)
    private int estimatedMinutes;

    @Column(name = "incident_explanation", nullable = false, columnDefinition = "text")
    private String incidentExplanation;

    @Column(name = "recommended_solution", nullable = false, columnDefinition = "text")
    private String recommendedSolution;

    @Column(name = "hint_penalty", nullable = false)
    private int hintPenalty;

    @Column(name = "out_of_order_penalty", nullable = false)
    private int outOfOrderPenalty;

    @Column(nullable = false)
    private boolean active;

    /** Content version, incremented on every edit; simulations remember the version they were started with. */
    @Column(nullable = false)
    private int version = 1;

    @Column(name = "source_scenario_id")
    private Long sourceScenarioId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "scenario", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<ScenarioObjective> objectives = new ArrayList<>();

    @OneToMany(mappedBy = "scenario", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<ScenarioResource> resources = new ArrayList<>();

    @OneToMany(mappedBy = "scenario", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<ScenarioEvent> events = new ArrayList<>();

    @OneToMany(mappedBy = "scenario", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<ScenarioAction> actions = new ArrayList<>();

    @OneToMany(mappedBy = "scenario", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<ScenarioHint> hints = new ArrayList<>();

    protected Scenario() {
    }

    public Scenario(String slug, Instant createdAt) {
        this.slug = slug;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public void updateDetails(String title, String summary, String description, Difficulty difficulty,
                              Category category, int estimatedMinutes, String incidentExplanation,
                              String recommendedSolution, int hintPenalty, int outOfOrderPenalty) {
        this.title = title;
        this.summary = summary;
        this.description = description;
        this.difficulty = difficulty;
        this.category = category;
        this.estimatedMinutes = estimatedMinutes;
        this.incidentExplanation = incidentExplanation;
        this.recommendedSolution = recommendedSolution;
        this.hintPenalty = hintPenalty;
        this.outOfOrderPenalty = outOfOrderPenalty;
    }

    /** Removes all children; the caller must flush before adding new ones (unique keys per scenario). */
    public void clearChildren() {
        objectives.clear();
        resources.clear();
        events.clear();
        actions.clear();
        hints.clear();
    }

    public void addObjective(ScenarioObjective objective) {
        objective.attachTo(this);
        objectives.add(objective);
    }

    public void addResource(ScenarioResource resource) {
        resource.attachTo(this);
        resources.add(resource);
    }

    public void addEvent(ScenarioEvent event) {
        event.attachTo(this);
        events.add(event);
    }

    public void addAction(ScenarioAction action) {
        action.attachTo(this);
        actions.add(action);
    }

    public void addHint(ScenarioHint hint) {
        hint.attachTo(this);
        hints.add(hint);
    }

    public Optional<ScenarioAction> findAction(String actionKey) {
        return actions.stream().filter(a -> a.getActionKey().equals(actionKey)).findFirst();
    }

    /** Maximum achievable score = sum of points of all expected actions. */
    public int maxScore() {
        return actions.stream()
                .filter(a -> a.getOutcome() == ScenarioEnums.ActionOutcome.EXPECTED)
                .mapToInt(ScenarioAction::getPoints)
                .sum();
    }

    public void touch(Instant now) {
        this.updatedAt = now;
    }

    public void incrementVersion() {
        this.version++;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setSourceScenarioId(Long sourceScenarioId) {
        this.sourceScenarioId = sourceScenarioId;
    }

    public Long getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public String getDescription() {
        return description;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public Category getCategory() {
        return category;
    }

    public int getEstimatedMinutes() {
        return estimatedMinutes;
    }

    public String getIncidentExplanation() {
        return incidentExplanation;
    }

    public String getRecommendedSolution() {
        return recommendedSolution;
    }

    public int getHintPenalty() {
        return hintPenalty;
    }

    public int getOutOfOrderPenalty() {
        return outOfOrderPenalty;
    }

    public boolean isActive() {
        return active;
    }

    public int getVersion() {
        return version;
    }

    public Long getSourceScenarioId() {
        return sourceScenarioId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<ScenarioObjective> getObjectives() {
        return objectives;
    }

    public List<ScenarioResource> getResources() {
        return resources;
    }

    public List<ScenarioEvent> getEvents() {
        return events;
    }

    public List<ScenarioAction> getActions() {
        return actions;
    }

    public List<ScenarioHint> getHints() {
        return hints;
    }
}
