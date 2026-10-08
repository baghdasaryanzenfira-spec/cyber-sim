package am.cybersim.integration;

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
 * One completed exam attempt submitted by the learner module (ADR-13).
 *
 * <p>The row keeps three layers apart on purpose: what the learner module <em>sent</em> ({@code actions},
 * {@code claimedScore}), what this platform <em>verified</em> by replaying the submission against the pinned
 * published version ({@code verification}, {@code verifiedScore} — the authoritative grade), and what the AI
 * <em>said about it</em> on an administrator's request ({@code review}, advisory only).
 */
@Entity
@Table(name = "student_attempts")
public class StudentAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scenario_id", nullable = false)
    private Long scenarioId;

    @Column(name = "scenario_version", nullable = false)
    private int scenarioVersion;

    /** The learner module's own attempt id; unique, so a retried submission cannot create a second row. */
    @Column(name = "external_id", nullable = false, unique = true)
    private String externalId;

    /** Opaque student identifier owned by the learner module; this platform stores no student accounts. */
    @Column(name = "student_ref", nullable = false)
    private String studentRef;

    @Column(name = "student_name")
    private String studentName;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    @Column(name = "hints_used", nullable = false)
    private int hintsUsed;

    /** The submitted action list, exactly as received (JSON). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String actions;

    @Column(name = "claimed_score")
    private Integer claimedScore;

    @Column(name = "verified_score", nullable = false)
    private int verifiedScore;

    /** Null when the learner module sent no claimed score. */
    @Column(name = "score_matches")
    private Boolean scoreMatches;

    /** This platform's deterministic replay result (JSON of {@code IntegrationDtos.Verification}). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String verification;

    /** AI review (JSON of {@code IntegrationDtos.StoredReview}); null until an administrator requests one. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String review;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    protected StudentAttempt() {
    }

    public StudentAttempt(Long scenarioId, int scenarioVersion, String externalId, String studentRef,
                          String studentName, Instant startedAt, Instant completedAt, int hintsUsed,
                          String actions, Integer claimedScore, int verifiedScore, Boolean scoreMatches,
                          String verification, Instant submittedAt) {
        this.scenarioId = scenarioId;
        this.scenarioVersion = scenarioVersion;
        this.externalId = externalId;
        this.studentRef = studentRef;
        this.studentName = studentName;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.hintsUsed = hintsUsed;
        this.actions = actions;
        this.claimedScore = claimedScore;
        this.verifiedScore = verifiedScore;
        this.scoreMatches = scoreMatches;
        this.verification = verification;
        this.submittedAt = submittedAt;
    }

    public void attachReview(String reviewJson, Long adminId, Instant at) {
        this.review = reviewJson;
        this.reviewedBy = adminId;
        this.reviewedAt = at;
    }

    public Long getId() {
        return id;
    }

    public Long getScenarioId() {
        return scenarioId;
    }

    public int getScenarioVersion() {
        return scenarioVersion;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getStudentRef() {
        return studentRef;
    }

    public String getStudentName() {
        return studentName;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public int getHintsUsed() {
        return hintsUsed;
    }

    public String getActions() {
        return actions;
    }

    public Integer getClaimedScore() {
        return claimedScore;
    }

    public int getVerifiedScore() {
        return verifiedScore;
    }

    public Boolean getScoreMatches() {
        return scoreMatches;
    }

    public String getVerification() {
        return verification;
    }

    public String getReview() {
        return review;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public Long getReviewedBy() {
        return reviewedBy;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }
}
