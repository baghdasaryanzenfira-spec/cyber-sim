package am.cybersim.scenario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Immutable snapshot of a scenario at the moment it was published. Consumers of published scenarios
 * (e.g. the trainee-facing simulation module) read these rows; later edits of the draft never change them.
 */
@Entity
@Table(name = "scenario_versions")
public class ScenarioVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scenario_id", nullable = false)
    private Long scenarioId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String definition;

    @Column(name = "quality_score", nullable = false)
    private int qualityScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String quality;

    @Column(name = "change_note")
    private String changeNote;

    @Column(name = "published_by")
    private Long publishedBy;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    protected ScenarioVersion() {
    }

    public ScenarioVersion(Long scenarioId, int versionNumber, String definitionJson, int qualityScore,
                           String qualityJson, String changeNote, Long publishedBy, Instant publishedAt) {
        this.scenarioId = scenarioId;
        this.versionNumber = versionNumber;
        this.definition = definitionJson;
        this.qualityScore = qualityScore;
        this.quality = qualityJson;
        this.changeNote = changeNote;
        this.publishedBy = publishedBy;
        this.publishedAt = publishedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getScenarioId() {
        return scenarioId;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    /** The frozen scenario definition as JSON text. */
    public String getDefinition() {
        return definition;
    }

    public int getQualityScore() {
        return qualityScore;
    }

    public String getQuality() {
        return quality;
    }

    public String getChangeNote() {
        return changeNote;
    }

    public Long getPublishedBy() {
        return publishedBy;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }
}
