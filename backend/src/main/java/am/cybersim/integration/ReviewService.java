package am.cybersim.integration;

import am.cybersim.ai.AiGateway;
import am.cybersim.ai.AiGateway.AiResult;
import am.cybersim.ai.AiOutputValidator;
import am.cybersim.ai.AiPayload;
import am.cybersim.ai.dto.AiReview;
import am.cybersim.ai.dto.ReviewSubject;
import am.cybersim.integration.dto.IntegrationDtos.AdminAttemptDetail;
import am.cybersim.integration.dto.IntegrationDtos.StoredReview;
import am.cybersim.integration.dto.IntegrationDtos.Verification;
import am.cybersim.scenario.ScenarioVersionRepository;
import am.cybersim.scenario.dto.ScenarioDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.List;

/**
 * The admin's "AI review" button (ADR-13). The AI writes the narrative — rating, message, strengths, mistakes,
 * recommendations — around a grade that is already fixed by the deterministic replay. The review is stored on
 * the attempt so the learner module can fetch it, and the call is audited in {@code ai_interactions} like every
 * other AI task. Pressing the button again replaces the previous review.
 */
@Service
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    private final AttemptService attemptService;
    private final StudentAttemptRepository attempts;
    private final ScenarioVersionRepository versions;
    private final AiGateway gateway;
    private final AiOutputValidator validator;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final TransactionTemplate tx;

    public ReviewService(AttemptService attemptService, StudentAttemptRepository attempts,
                         ScenarioVersionRepository versions, AiGateway gateway, AiOutputValidator validator,
                         ObjectMapper objectMapper, Clock clock, PlatformTransactionManager transactionManager) {
        this.attemptService = attemptService;
        this.attempts = attempts;
        this.versions = versions;
        this.gateway = gateway;
        this.validator = validator;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** The AI call runs outside any transaction; only reading the attempt and storing the review are inside. */
    public AdminAttemptDetail review(Long adminId, Long attemptId) {
        record Source(StudentAttempt attempt, ScenarioDefinition definition, Verification verification) {
        }
        Source source = tx.execute(status -> {
            AdminAttemptDetail detail = attemptService.detail(attemptId);
            StudentAttempt attempt = attempts.findById(attemptId).orElseThrow();
            var version = versions.findByScenarioIdAndVersionNumber(attempt.getScenarioId(),
                    attempt.getScenarioVersion()).orElseThrow();
            return new Source(attempt,
                    objectMapper.readValue(version.getDefinition(), ScenarioDefinition.class),
                    detail.verification());
        });

        ReviewSubject subject = subject(source.attempt(), source.verification());
        AiResult<AiReview> result = gateway.execute(new AiPayload.Review(source.definition(), subject),
                new AiGateway.CallContext(adminId, source.attempt().getScenarioId(),
                        "Review of attempt " + source.attempt().getExternalId()),
                validator::validateReview);

        AiReview review = result.value();
        StoredReview stored = new StoredReview(review.rating(), review.message(), review.strengths(),
                review.mistakes(), review.recommendations(), result.source());
        tx.executeWithoutResult(status -> {
            StudentAttempt attempt = attempts.findById(attemptId).orElseThrow();
            attempt.attachReview(objectMapper.writeValueAsString(stored), adminId, clock.instant());
            attempts.save(attempt);
        });
        log.info("Admin {} reviewed attempt '{}' (rating {}, source {})", adminId,
                source.attempt().getExternalId(), review.rating(), result.source());
        return attemptService.detail(attemptId);
    }

    private static ReviewSubject subject(StudentAttempt attempt, Verification verification) {
        List<ReviewSubject.Step> steps = verification.steps().stream()
                .map(s -> new ReviewSubject.Step(s.sequence(), s.label(), s.outcome(), s.points(),
                        s.outOfOrder(), s.duplicate()))
                .toList();
        List<ReviewSubject.Missed> missed = verification.missedActions().stream()
                .map(m -> new ReviewSubject.Missed(m.label(), m.points(), m.explanation()))
                .toList();
        return new ReviewSubject(attempt.getStudentName(), verification.scorePercent(),
                attempt.getClaimedScore(), attempt.getHintsUsed(), verification.evidenceRevealed(),
                verification.evidenceTotal(), steps, missed);
    }
}
