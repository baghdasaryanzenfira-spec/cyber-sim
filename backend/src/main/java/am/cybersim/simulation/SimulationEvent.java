package am.cybersim.simulation;

import am.cybersim.scenario.ScenarioEnums.EventType;
import am.cybersim.scenario.ScenarioEnums.Severity;
import am.cybersim.scenario.ScenarioEvent;
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
 * An event visible to the student: a materialised scenario log/alert, or a SYSTEM event created by the engine
 * when the analyst performs an action. {@code flagged} is set by the student (evidence board);
 * {@code evidence} is the scenario author's ground truth and is only shown after completion.
 */
@Entity
@Table(name = "simulation_events")
public class SimulationEvent {

    public static final String ANALYST_SOURCE = "analyst-console";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "simulation_id")
    private Simulation simulation;

    @Column(name = "event_key")
    private String eventKey;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private EventType eventType;

    @Column(nullable = false)
    private String source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;

    @Column(name = "resource_key")
    private String resourceKey;

    @Column(nullable = false, columnDefinition = "text")
    private String message;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> details = new LinkedHashMap<>();

    @Column(nullable = false)
    private boolean evidence;

    @Column(nullable = false)
    private boolean flagged;

    @Column(name = "revealed_at", nullable = false)
    private Instant revealedAt;

    protected SimulationEvent() {
    }

    static SimulationEvent materialize(ScenarioEvent template, Instant incidentStart, Instant revealedAt) {
        SimulationEvent e = new SimulationEvent();
        e.eventKey = template.getEventKey();
        e.occurredAt = incidentStart.plusSeconds(template.getOffsetSeconds());
        e.eventType = template.getEventType();
        e.source = template.getSource();
        e.severity = template.getSeverity();
        e.resourceKey = template.getResourceKey();
        e.message = template.getMessage();
        e.details = new LinkedHashMap<>(template.getDetails());
        e.evidence = template.isEvidence();
        e.revealedAt = revealedAt;
        return e;
    }

    static SimulationEvent analystAction(String message, String resourceKey, String actionKey, Instant at) {
        SimulationEvent e = new SimulationEvent();
        e.occurredAt = at;
        e.eventType = EventType.SYSTEM;
        e.source = ANALYST_SOURCE;
        e.severity = Severity.INFO;
        e.resourceKey = resourceKey;
        e.message = message;
        e.details = new LinkedHashMap<>(Map.of("actionKey", actionKey));
        e.revealedAt = at;
        return e;
    }

    void attachTo(Simulation simulation) {
        this.simulation = simulation;
    }

    void setFlagged(boolean flagged) {
        this.flagged = flagged;
    }

    public Long getId() {
        return id;
    }

    public String getEventKey() {
        return eventKey;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public EventType getEventType() {
        return eventType;
    }

    public String getSource() {
        return source;
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getResourceKey() {
        return resourceKey;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public boolean isEvidence() {
        return evidence;
    }

    public boolean isFlagged() {
        return flagged;
    }

    public Instant getRevealedAt() {
        return revealedAt;
    }
}
