package am.cybersim.ai;

import am.cybersim.ai.AiGateway.AiResult;
import am.cybersim.ai.AiGateway.CallContext;
import am.cybersim.ai.context.SimulationSnapshot;
import am.cybersim.ai.dto.AiDtos.AiSource;
import am.cybersim.ai.dto.AiDtos.AssistantMessage;
import am.cybersim.ai.dto.AiDtos.Recommendation;
import am.cybersim.ai.dto.AiFeedback;
import am.cybersim.progress.ProgressStats;
import am.cybersim.scoring.ScoreResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Public API of the AI module: the AI-tutor capabilities A–D of requirement §8.
 * Callers pass snapshots; this service is free of any simulation persistence logic.
 */
@Service
public class TutorService {

    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}&&[^\n]]");

    private final AiGateway gateway;
    private final AiOutputValidator validator;
    private final AiInteractionRepository interactions;

    public TutorService(AiGateway gateway, AiOutputValidator validator, AiInteractionRepository interactions) {
        this.gateway = gateway;
        this.validator = validator;
        this.interactions = interactions;
    }

    /** A. Contextual hint without revealing the solution. */
    public AiResult<String> hint(SimulationSnapshot snapshot, int hintNumber) {
        return gateway.execute(new AiPayload.Hint(snapshot, hintNumber),
                context(snapshot, "Hint #" + hintNumber),
                text -> validator.validateHint(text, snapshot));
    }

    /** A'. Free question about the current simulation. */
    public AiResult<String> ask(SimulationSnapshot snapshot, String question) {
        String sanitized = CONTROL_CHARS.matcher(question).replaceAll("").strip();
        return gateway.execute(new AiPayload.Question(snapshot, sanitized), context(snapshot, sanitized),
                validator::validateAnswer);
    }

    /** B. Post-simulation analysis. */
    public AiResult<AiFeedback> feedback(SimulationSnapshot snapshot, ScoreResult score) {
        return gateway.execute(new AiPayload.Feedback(snapshot, score),
                context(snapshot, "Post-simulation analysis (score " + score.scorePercent() + ")"),
                validator::validateFeedback);
    }

    /** D. Learning recommendations from the student's statistics. */
    public AiResult<List<Recommendation>> recommendations(Long userId, ProgressStats stats) {
        return gateway.execute(new AiPayload.Recommendation(stats),
                new CallContext(userId, null, null, "Learning recommendations"),
                validator::validateRecommendations);
    }

    @Transactional(readOnly = true)
    public List<AssistantMessage> conversation(Long simulationId) {
        return interactions.findBySimulationIdAndInteractionTypeInOrderByCreatedAtAsc(simulationId,
                        EnumSet.of(AiTask.HINT, AiTask.QUESTION)).stream()
                .map(i -> new AssistantMessage(i.getId(), i.getInteractionType().name(),
                        i.getInteractionType() == AiTask.QUESTION ? i.getRequestText() : null,
                        i.getResponseText(), sourceOf(i), i.getCreatedAt()))
                .toList();
    }

    static AiSource sourceOf(AiInteraction i) {
        if (i.getStatus() == AiInteraction.Status.FALLBACK) {
            return AiSource.FALLBACK;
        }
        return i.getProvider() == AiProvider.Type.MOCK ? AiSource.MOCK : AiSource.AI;
    }

    private static CallContext context(SimulationSnapshot s, String requestText) {
        return new CallContext(s.userId(), s.simulationId(), s.scenario().id(), requestText);
    }
}
