package am.cybersim.integration;

import am.cybersim.authoring.ScenarioTestRunner;
import am.cybersim.common.ApiException;
import am.cybersim.integration.dto.IntegrationDtos.AdminAttemptDetail;
import am.cybersim.integration.dto.IntegrationDtos.AdminAttemptRow;
import am.cybersim.integration.dto.IntegrationDtos.AttemptReceipt;
import am.cybersim.integration.dto.IntegrationDtos.PageView;
import am.cybersim.integration.dto.IntegrationDtos.StoredReview;
import am.cybersim.integration.dto.IntegrationDtos.SubmitAttemptRequest;
import am.cybersim.integration.dto.IntegrationDtos.SubmittedAction;
import am.cybersim.integration.dto.IntegrationDtos.Verification;
import am.cybersim.integration.dto.IntegrationDtos.VerificationStep;
import am.cybersim.scenario.Scenario;
import am.cybersim.scenario.ScenarioRepository;
import am.cybersim.scenario.ScenarioVersion;
import am.cybersim.scenario.ScenarioVersionRepository;
import am.cybersim.scenario.dto.ScenarioDefinition;
import am.cybersim.scenario.dto.ScenarioDefinition.ActionDef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Receives completed exam attempts from the learner module and verifies them (ADR-13).
 *
 * <p>Verification is the whole point: the submitted action sequence is replayed against the <em>pinned published
 * version</em> of the scenario with the same deterministic rules the test runner uses, and the score this
 * platform computes is the authoritative one. A claimed score is only compared, never stored as the result.
 */
@Service
public class AttemptService {

    private static final Logger log = LoggerFactory.getLogger(AttemptService.class);

    private final StudentAttemptRepository attempts;
    private final ScenarioRepository scenarios;
    private final ScenarioVersionRepository versions;
    private final ScenarioTestRunner runner;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AttemptService(StudentAttemptRepository attempts, ScenarioRepository scenarios,
                          ScenarioVersionRepository versions, ScenarioTestRunner runner,
                          ObjectMapper objectMapper, Clock clock) {
        this.attempts = attempts;
        this.scenarios = scenarios;
        this.versions = versions;
        this.runner = runner;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ submission (learner module)

    @Transactional
    public AttemptReceipt submit(SubmitAttemptRequest request) {
        attempts.findByExternalId(request.externalId()).ifPresent(existing -> {
            throw new ApiException(HttpStatus.CONFLICT, "ATTEMPT_EXISTS",
                    "Attempt '" + request.externalId() + "' was already submitted; fetch it with "
                            + "GET /api/learner/attempts/" + request.externalId());
        });
        Scenario scenario = scenarios.findBySlug(request.scenarioSlug())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SCENARIO_NOT_FOUND",
                        "No scenario with slug '" + request.scenarioSlug() + "'"));
        Integer versionNumber = request.scenarioVersion() != null
                ? request.scenarioVersion() : scenario.getPublishedVersion();
        if (versionNumber == null) {
            throw new ApiException(HttpStatus.CONFLICT, "SCENARIO_NOT_PUBLISHED",
                    "Scenario '" + request.scenarioSlug() + "' has no published version");
        }
        ScenarioVersion version = versions.findByScenarioIdAndVersionNumber(scenario.getId(), versionNumber)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "VERSION_NOT_FOUND",
                        "Scenario '" + request.scenarioSlug() + "' has no version " + versionNumber));

        ScenarioDefinition definition = objectMapper.readValue(version.getDefinition(), ScenarioDefinition.class);
        Verification verification = verify(definition, request.actions(), request.hintsUsed());

        Boolean matches = request.claimedScorePercent() == null ? null
                : request.claimedScorePercent() == verification.scorePercent();
        StudentAttempt saved = attempts.save(new StudentAttempt(scenario.getId(), versionNumber,
                request.externalId(), request.studentRef(), request.studentName(), request.startedAt(),
                request.completedAt(), request.hintsUsed(), objectMapper.writeValueAsString(request.actions()),
                request.claimedScorePercent(), verification.scorePercent(), matches,
                objectMapper.writeValueAsString(verification), clock.instant()));
        log.info("Learner module submitted attempt '{}' for '{}' v{}: verified {}/100 (claimed {}, match {})",
                saved.getExternalId(), request.scenarioSlug(), versionNumber, verification.scorePercent(),
                request.claimedScorePercent(), matches);
        return receipt(saved, scenario.getSlug(), verification);
    }

    /** Replays the submitted keys with the test runner's rules; an unknown key is a client error, not a crash. */
    private Verification verify(ScenarioDefinition definition, List<SubmittedAction> submitted, int hintsUsed) {
        Map<String, ActionDef> catalogue = definition.actions().stream()
                .collect(Collectors.toMap(ActionDef::key, Function.identity()));
        List<ActionDef> order = submitted.stream().map(a -> {
            ActionDef def = catalogue.get(a.actionKey());
            if (def == null) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "UNKNOWN_ACTION",
                        "Action '" + a.actionKey() + "' does not exist in this scenario version");
            }
            return def;
        }).toList();

        ScenarioTestRunner.Play play = runner.play(definition, order, hintsUsed);
        List<VerificationStep> steps = play.steps().stream()
                .map(s -> new VerificationStep(s.sequence(), s.actionKey(), s.label(), s.outcome(), s.points(),
                        s.outOfOrder(), s.duplicate()))
                .toList();
        return new Verification(play.score().scorePercent(), play.score().rawScore(), play.score().maxScore(),
                hintsUsed, play.score().hintPenaltyTotal(), play.evidenceRevealed(), play.evidenceTotal(),
                steps, play.score().missedActions());
    }

    // ------------------------------------------------------------------ reads

    @Transactional(readOnly = true)
    public AttemptReceipt receiptByExternalId(String externalId) {
        StudentAttempt attempt = byExternalId(externalId);
        Scenario scenario = scenarios.findById(attempt.getScenarioId()).orElseThrow();
        return receipt(attempt, scenario.getSlug(), readVerification(attempt));
    }

    @Transactional(readOnly = true)
    public PageView<AdminAttemptRow> list(Long scenarioId, int page, int size) {
        var pageable = PageRequest.of(page, Math.min(size, 100));
        var result = scenarioId == null
                ? attempts.findAllByOrderBySubmittedAtDesc(pageable)
                : attempts.findByScenarioIdOrderBySubmittedAtDesc(scenarioId, pageable);
        Map<Long, String> titles = scenarios.findAllById(
                        result.map(StudentAttempt::getScenarioId).toSet()).stream()
                .collect(Collectors.toMap(Scenario::getId, Scenario::getTitle));
        List<AdminAttemptRow> rows = result.map(a -> row(a, titles.getOrDefault(a.getScenarioId(), "?"))).toList();
        return new PageView<>(rows, result.getTotalElements(), page, result.getSize());
    }

    @Transactional(readOnly = true)
    public AdminAttemptDetail detail(Long id) {
        StudentAttempt attempt = attempts.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ATTEMPT_NOT_FOUND",
                        "No attempt with id " + id));
        Scenario scenario = scenarios.findById(attempt.getScenarioId()).orElseThrow();
        List<SubmittedAction> submitted = objectMapper.readValue(attempt.getActions(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, SubmittedAction.class));
        return new AdminAttemptDetail(row(attempt, scenario.getTitle()), submitted,
                readVerification(attempt), readReview(attempt), attempt.getReviewedAt());
    }

    StudentAttempt byExternalId(String externalId) {
        return attempts.findByExternalId(externalId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ATTEMPT_NOT_FOUND",
                        "No attempt '" + externalId + "'"));
    }

    Verification readVerification(StudentAttempt attempt) {
        return objectMapper.readValue(attempt.getVerification(), Verification.class);
    }

    StoredReview readReview(StudentAttempt attempt) {
        return attempt.getReview() == null ? null
                : objectMapper.readValue(attempt.getReview(), StoredReview.class);
    }

    private AttemptReceipt receipt(StudentAttempt a, String slug, Verification verification) {
        return new AttemptReceipt(a.getExternalId(), slug, a.getScenarioVersion(), a.getVerifiedScore(),
                a.getScoreMatches(), verification, readReview(a), a.getSubmittedAt());
    }

    private AdminAttemptRow row(StudentAttempt a, String scenarioTitle) {
        StoredReview review = readReview(a);
        return new AdminAttemptRow(a.getId(), a.getExternalId(), a.getStudentRef(), a.getStudentName(),
                a.getScenarioId(), scenarioTitle, a.getScenarioVersion(), a.getClaimedScore(),
                a.getVerifiedScore(), a.getScoreMatches(), review != null,
                review == null ? null : review.rating(), a.getHintsUsed(), a.getSubmittedAt());
    }
}
