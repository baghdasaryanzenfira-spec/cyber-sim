package am.cybersim.simulation;

import am.cybersim.scenario.Scenario;
import am.cybersim.user.User;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * One training attempt of one student on one scenario (aggregate root of the simulation instance).
 * Optimistic locking ({@code lock_version}) prevents two parallel requests (e.g. double click) from
 * corrupting the state.
 */
@Entity
@Table(name = "simulations")
public class Simulation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scenario_id")
    private Scenario scenario;

    @Column(name = "scenario_version", nullable = false)
    private int scenarioVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SimulationStatus status;

    @Column(name = "hints_used", nullable = false)
    private int hintsUsed;

    /** Simulated time at which the attack began; log timestamps are computed relative to it. */
    @Column(name = "incident_started_at")
    private Instant incidentStartedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    @Column(name = "lock_version", nullable = false)
    private long lockVersion;

    @OneToMany(mappedBy = "simulation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<SimulationResource> resources = new ArrayList<>();

    @OneToMany(mappedBy = "simulation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("occurredAt, id")
    private List<SimulationEvent> events = new ArrayList<>();

    @OneToMany(mappedBy = "simulation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequence")
    private List<SimulationAction> actions = new ArrayList<>();

    protected Simulation() {
    }

    public Simulation(User user, Scenario scenario, Instant createdAt) {
        this.user = user;
        this.scenario = scenario;
        this.scenarioVersion = scenario.getVersion();
        this.status = SimulationStatus.CREATED;
        this.createdAt = createdAt;
    }

    void markStarted(Instant startedAt, Instant incidentStartedAt) {
        this.status = SimulationStateMachine.start(status);
        this.startedAt = startedAt;
        this.incidentStartedAt = incidentStartedAt;
    }

    void changeStatus(SimulationStatus newStatus) {
        this.status = newStatus;
    }

    void markCompleted(Instant at) {
        this.status = SimulationStateMachine.complete(status);
        this.completedAt = at;
    }

    void markAbandoned(Instant at) {
        this.status = SimulationStateMachine.abandon(status);
        this.completedAt = at;
    }

    void incrementHintsUsed() {
        this.hintsUsed++;
    }

    void addResource(SimulationResource resource) {
        resource.attachTo(this);
        resources.add(resource);
    }

    void addEvent(SimulationEvent event) {
        event.attachTo(this);
        events.add(event);
    }

    void addAction(SimulationAction action) {
        action.attachTo(this);
        actions.add(action);
    }

    public boolean hasApplied(String actionKey) {
        return actions.stream().anyMatch(a -> a.getActionKey().equals(actionKey) && !a.isDuplicate());
    }

    public boolean hasEvent(String eventKey) {
        return events.stream().anyMatch(e -> eventKey.equals(e.getEventKey()));
    }

    public Optional<SimulationResource> findResource(String key) {
        return resources.stream().filter(r -> r.getResourceKey().equals(key)).findFirst();
    }

    public Optional<SimulationEvent> findEvent(Long eventId) {
        return events.stream().filter(e -> e.getId().equals(eventId)).findFirst();
    }

    public boolean isOwnedBy(Long userId) {
        return user.getId().equals(userId);
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Scenario getScenario() {
        return scenario;
    }

    public int getScenarioVersion() {
        return scenarioVersion;
    }

    public SimulationStatus getStatus() {
        return status;
    }

    public int getHintsUsed() {
        return hintsUsed;
    }

    public Instant getIncidentStartedAt() {
        return incidentStartedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public List<SimulationResource> getResources() {
        return resources;
    }

    public List<SimulationEvent> getEvents() {
        return events;
    }

    public List<SimulationAction> getActions() {
        return actions;
    }
}
