package am.cybersim.integration;

import am.cybersim.ai.AiOutputValidator;
import am.cybersim.ai.AiProviderException;
import am.cybersim.ai.MockAiProvider;
import am.cybersim.ai.dto.AiReview;
import am.cybersim.ai.dto.ReviewSubject;
import am.cybersim.authoring.ScenarioTestRunner;
import am.cybersim.scenario.ScenarioEnums.ActionOutcome;
import am.cybersim.scenario.dto.ScenarioDefinition;
import am.cybersim.scenario.dto.ScenarioDefinition.ActionDef;
import am.cybersim.scoring.ScoringEngine;
import am.cybersim.support.TestDefinitions;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The verification core of the learner integration (ADR-13): the replay that turns a submitted action list
 * into the authoritative score, and the validation of the AI review around it.
 */
class AttemptVerificationTest {

    final ScenarioTestRunner runner = new ScenarioTestRunner(new ScoringEngine());
    final ScenarioDefinition def = TestDefinitions.small();

    List<ActionDef> byKeys(String... keys) {
        return List.of(keys).stream()
                .map(k -> def.actions().stream().filter(a -> a.key().equals(k)).findFirst().orElseThrow())
                .toList();
    }

    @Test
    void replayOfTheCorrectSequenceReachesFullScore() {
        var expected = def.actions().stream().filter(a -> a.outcome() == ActionOutcome.EXPECTED).toList();
        var play = runner.play(def, expected, 0);
        assertThat(play.score().scorePercent()).isEqualTo(100);
        assertThat(play.score().missedActions()).isEmpty();
    }

    @Test
    void hintsReduceTheVerifiedScore() {
        var expected = def.actions().stream().filter(a -> a.outcome() == ActionOutcome.EXPECTED).toList();
        var withHints = runner.play(def, expected, 2);
        var without = runner.play(def, expected, 0);
        assertThat(withHints.score().hintPenaltyTotal()).isEqualTo(2 * def.hintPenalty());
        assertThat(withHints.score().scorePercent()).isLessThan(without.score().scorePercent());
    }

    @Test
    void harmfulAndDuplicateActionsAreScoredLikeTheLearnerRuntimeWould() {
        var harmful = def.actions().stream().filter(a -> a.outcome() == ActionOutcome.HARMFUL).findFirst().orElseThrow();
        var play = runner.play(def, List.of(harmful, harmful), 0);
        var steps = play.steps();
        assertThat(steps.get(0).points()).isNegative();
        assertThat(steps.get(1).duplicate()).isTrue();
        assertThat(steps.get(1).points()).isZero();
        assertThat(play.score().scorePercent()).isZero();
    }

    @Test
    void mockReviewIsBuiltFromTheVerifiedFacts() {
        var expected = def.actions().stream().filter(a -> a.outcome() == ActionOutcome.EXPECTED).toList();
        var play = runner.play(def, expected.subList(0, 1), 1);
        var subject = new ReviewSubject("Demo Student", play.score().scorePercent(), 85, 1,
                play.evidenceRevealed(), play.evidenceTotal(),
                play.steps().stream().map(s -> new ReviewSubject.Step(s.sequence(), s.label(), s.outcome(),
                        s.points(), s.outOfOrder(), s.duplicate())).toList(),
                play.score().missedActions().stream()
                        .map(m -> new ReviewSubject.Missed(m.label(), m.points(), m.explanation())).toList());
        AiReview review = new MockAiProvider(JsonMapper.builder().build()).review(subject);
        assertThat(review.rating()).isEqualTo(play.score().scorePercent());
        assertThat(review.message()).contains(play.score().scorePercent() + "/100");
        assertThat(review.mistakes()).isNotEmpty();
    }

    @Test
    void reviewValidationRejectsARatingOutsideTheScale() {
        var validator = new AiOutputValidator(JsonMapper.builder().build());
        assertThatThrownBy(() -> validator.validateReview(
                "{\"rating\":140,\"message\":\"great\",\"strengths\":[],\"mistakes\":[],\"recommendations\":[]}"))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("outside 0-100");
    }

    @Test
    void reviewValidationCapsListSizes() {
        var validator = new AiOutputValidator(JsonMapper.builder().build());
        String many = "\"" + "x".repeat(10) + "\",".repeat(9);
        AiReview review = validator.validateReview(
                "{\"rating\":70,\"message\":\"ok\",\"strengths\":[" + many.substring(0, many.length() - 1)
                        + "],\"mistakes\":[],\"recommendations\":[]}");
        assertThat(review.strengths()).hasSize(5);
    }
}
