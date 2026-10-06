package am.cybersim.scenario;

import am.cybersim.scenario.ScenarioEnums.EventType;
import am.cybersim.scenario.ScenarioEnums.Severity;
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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One entry of the incident timeline (log line or alert).
 * {@code revealedByActionKey == null} → visible when the simulation starts; otherwise it becomes visible
 * when the student performs that investigation action.
 */
@Entity
@Table(name = "scenario_events")
public class ScenarioEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scenario_id")
    private Scenario scenario;

    @Column(nullable = false)
    private int position;

    @Column(name = "event_key", nullable = false)
    private String eventKey;

    @Column(name = "offset_seconds", nullable = false)
    private int offsetSeconds;

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

    @Column(name = "evidence_note", columnDefinition = "text")
    private String evidenceNote;

    @Column(name = "revealed_by_action_key")
    private String revealedByActionKey;

    protected ScenarioEvent() {
    }

    public ScenarioEvent(int position, String eventKey, int offsetSeconds, EventType eventType, String source,
                         Severity severity, String resourceKey, String message, Map<String, Object> details,
                         boolean evidence, String evidenceNote, String revealedByActionKey) {
        this.position = position;
        this.eventKey = eventKey;
        this.offsetSeconds = offsetSeconds;
        this.eventType = eventType;
        this.source = source;
        this.severity = severity;
        this.resourceKey = resourceKey;
        this.message = message;
        this.details = details == null ? new LinkedHashMap<>() : new LinkedHashMap<>(details);
        this.evidence = evidence;
        this.evidenceNote = evidenceNote;
        this.revealedByActionKey = revealedByActionKey;
    }

    void attachTo(Scenario scenario) {
        this.scenario = scenario;
    }

    public boolean isInitiallyVisible() {
        return revealedByActionKey == null;
    }

    public int getPosition() {
        return position;
    }

    public String getEventKey() {
        return eventKey;
    }

    public int getOffsetSeconds() {
        return offsetSeconds;
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

    public String getEvidenceNote() {
        return evidenceNote;
    }

    public String getRevealedByActionKey() {
        return revealedByActionKey;
    }
}
