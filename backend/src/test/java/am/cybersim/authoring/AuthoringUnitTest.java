package am.cybersim.authoring;

import am.cybersim.authoring.ScenarioAnalyzer.Issue;
import am.cybersim.authoring.ScenarioAnalyzer.ValidationReport;
import am.cybersim.authoring.ScenarioTestRunner.PathResult;
import am.cybersim.authoring.ScenarioTestRunner.TestRunReport;
import am.cybersim.scenario.ScenarioDefinitionValidator;
import am.cybersim.scenario.ScenarioEnums.ActionOutcome;
import am.cybersim.scenario.ScenarioEnums.ActionPhase;
import am.cybersim.scenario.dto.ScenarioDefinition;
import am.cybersim.scenario.dto.ScenarioDefinition.ActionDef;
import am.cybersim.scoring.ScoringEngine;
import am.cybersim.support.TestDefinitions;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Graph validation, test runner and quality score on a tiny fixture and on the three real seed scenarios. */
class AuthoringUnitTest {

    static ValidatorFactory factory;
    static ScenarioAnalyzer analyzer;
    static ScenarioTestRunner runner;
    static QualityScorer scorer;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        analyzer = new ScenarioAnalyzer(new ScenarioDefinitionValidator(factory.getValidator()));
        runner = new ScenarioTestRunner(new ScoringEngine());
        scorer = new QualityScorer();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    static boolean has(ValidationReport report, String code) {
        return report.issues().stream().map(Issue::code).anyMatch(code::equals);
    }

    // ------------------------------------------------------------------ validation

    @Test
    void smallFixtureIsValid() {
        ValidationReport report = analyzer.analyze(TestDefinitions.small());
        assertThat(report.valid()).as(report.issues().toString()).isTrue();
        assertThat(report.stats().expectedActions()).isEqualTo(3);
        assertThat(report.stats().maxDepth()).isEqualTo(3);
    }

    @Test
    void structuralErrorsAreReportedAndSkipGraphRules() {
        ScenarioDefinition broken = TestDefinitions.mapActions(TestDefinitions.small(),
                a -> a.stream().map(x -> x.key().equals("isolate-vm")
                        ? TestDefinitions.action("isolate-vm", "Isolate VM", ActionPhase.RESPONSE, x.category(),
                                ActionOutcome.EXPECTED, 30, "does-not-exist", "ISOLATED") : x).toList());
        ValidationReport report = analyzer.analyze(broken);
        assertThat(report.valid()).isFalse();
        assertThat(has(report, "STRUCTURE")).isTrue();
        assertThat(report.stats()).isNull();
    }

    @Test
    void correctActionDependingOnAHarmfulOneIsAnError() {
        ScenarioDefinition def = TestDefinitions.mapActions(TestDefinitions.small(),
                a -> a.stream().map(x -> x.key().equals("isolate-vm")
                        ? TestDefinitions.action("isolate-vm", "Isolate VM", ActionPhase.RESPONSE, x.category(),
                                ActionOutcome.EXPECTED, 30, "delete-vm", "ISOLATED") : x).toList());
        assertThat(has(analyzer.analyze(def), "EXPECTED_DEPENDS_ON_UNEXPECTED")).isTrue();
    }

    @Test
    void investigationDependingOnResponseIsAnError() {
        ScenarioDefinition def = TestDefinitions.mapActions(TestDefinitions.small(),
                a -> a.stream().map(x -> x.key().equals("inspect-log")
                        ? TestDefinitions.action("inspect-log", "Inspect log", ActionPhase.INVESTIGATION, x.category(),
                                ActionOutcome.EXPECTED, 10, "isolate-vm", null) : x).toList());
        ValidationReport report = analyzer.analyze(def);
        assertThat(report.valid()).isFalse();
    }

    @Test
    void scenarioWithoutHarmfulActionHasNoDangerousPath() {
        ScenarioDefinition def = TestDefinitions.mapActions(TestDefinitions.small(),
                a -> a.stream().filter(x -> x.outcome() != ActionOutcome.HARMFUL).toList());
        ValidationReport report = analyzer.analyze(def);
        assertThat(report.valid()).isFalse();
        assertThat(has(report, "NO_DANGEROUS_PATH")).isTrue();
    }

    @Test
    void evidenceOnlyRevealedByAWrongActionIsAnError() {
        ScenarioDefinition def = TestDefinitions.mapEvents(TestDefinitions.small(), events -> events.stream()
                .map(e -> e.key().equals("e-log")
                        ? new ScenarioDefinition.EventDef(e.key(), e.offsetSeconds(), e.type(), e.source(),
                                e.severity(), e.resourceKey(), e.message(), e.details(), true, "note", "check-other")
                        : e).toList());
        assertThat(has(analyzer.analyze(def), "EVENT_ONLY_VIA_UNEXPECTED_ACTION")).isTrue();
    }

    @Test
    void weaknessesAreWarningsNotErrors() {
        ValidationReport report = analyzer.analyze(TestDefinitions.small());
        assertThat(report.warningCount()).isPositive(); // few evidence events, short fixture
        assertThat(report.errorCount()).isZero();
    }

    // ------------------------------------------------------------------ graph

    @Test
    void graphContainsAllNodesAndTheDependencyEdges() {
        ScenarioGraph graph = ScenarioGraph.build(TestDefinitions.small());
        assertThat(graph.nodes()).extracting(ScenarioGraph.Node::id).contains("start", "action:isolate-vm",
                "event:e-log", "resource:vm-1");
        assertThat(graph.edges()).contains(
                new ScenarioGraph.Edge("action:identify-vm", "action:isolate-vm", ScenarioGraph.EdgeType.PREREQUISITE),
                new ScenarioGraph.Edge("action:inspect-log", "event:e-log", ScenarioGraph.EdgeType.REVEALS),
                new ScenarioGraph.Edge("start", "event:e-alert", ScenarioGraph.EdgeType.INITIAL),
                new ScenarioGraph.Edge("action:isolate-vm", "resource:vm-1", ScenarioGraph.EdgeType.EFFECT));
    }

    @Test
    void topologicalOrderPutsPrerequisitesFirst() {
        List<String> order = ScenarioGraph.topologicalOrder(TestDefinitions.small(), ScenarioGraph::isExpected)
                .stream().map(ActionDef::key).toList();
        assertThat(order).containsExactly("inspect-log", "identify-vm", "isolate-vm");
    }

    // ------------------------------------------------------------------ test runner

    @Test
    void correctAndDangerousPathPassOnTheFixture() {
        TestRunReport report = runner.run(TestDefinitions.small());
        assertThat(report.passed()).as(report.toString()).isTrue();
        PathResult correct = report.paths().getFirst();
        assertThat(correct.scorePercent()).isEqualTo(100);
        assertThat(correct.evidenceRevealed()).isEqualTo(correct.evidenceTotal());
        PathResult dangerous = report.paths().get(1);
        assertThat(dangerous.scorePercent()).isZero();
        assertThat(dangerous.steps()).allMatch(s -> s.points() < 0);
    }

    @Test
    void dangerousPathFailsWhenAHarmfulActionProducesTheFixedState() {
        ScenarioDefinition def = TestDefinitions.mapActions(TestDefinitions.small(),
                a -> a.stream().map(x -> x.key().equals("delete-vm")
                        ? TestDefinitions.action("delete-vm", "Delete VM", ActionPhase.RESPONSE, x.category(),
                                ActionOutcome.HARMFUL, -10, null, "ISOLATED") : x).toList());
        PathResult dangerous = runner.run(def).paths().get(1);
        assertThat(dangerous.passed()).isFalse();
        assertThat(dangerous.checks()).anyMatch(c -> !c.passed() && c.name().contains("look like the fix"));
    }

    @Test
    void correctPathFailsWhenEvidenceCannotBeCollected() {
        // evidence revealed by an action that no longer exists on the correct path
        ScenarioDefinition def = TestDefinitions.mapEvents(TestDefinitions.small(), events -> events.stream()
                .map(e -> e.key().equals("e-log")
                        ? new ScenarioDefinition.EventDef(e.key(), e.offsetSeconds(), e.type(), e.source(),
                                e.severity(), e.resourceKey(), e.message(), e.details(), true, "note", "check-other")
                        : e).toList());
        PathResult correct = runner.run(def).paths().getFirst();
        assertThat(correct.passed()).isFalse();
        assertThat(correct.evidenceRevealed()).isLessThan(correct.evidenceTotal());
    }

    // ------------------------------------------------------------------ real scenarios

    @ParameterizedTest
    @ValueSource(strings = {"01-ssh-bruteforce-vm", "02-compromised-credentials", "03-public-storage-bucket"})
    void everySeedScenarioIsValidPassesBothPathsAndIsPublishable(String name) {
        ScenarioDefinition def = TestDefinitions.seed(name);
        ValidationReport validation = analyzer.analyze(def);
        assertThat(validation.valid()).as(validation.issues().toString()).isTrue();
        TestRunReport tests = runner.run(def);
        assertThat(tests.passed()).as(tests.toString()).isTrue();
        QualityScorer.QualityReport quality = scorer.score(def, validation, tests);
        assertThat(quality.score()).as(quality.toString()).isGreaterThanOrEqualTo(QualityScorer.PUBLISH_THRESHOLD);
        assertThat(quality.meetsPublishThreshold()).isTrue();
    }

    // ------------------------------------------------------------------ quality score

    @Test
    void invalidScenarioScoresLowAndZeroesTheDependentParts() {
        ScenarioDefinition def = TestDefinitions.mapActions(TestDefinitions.small(),
                a -> a.stream().filter(x -> x.outcome() != ActionOutcome.HARMFUL).toList());
        ValidationReport validation = analyzer.analyze(def);
        QualityScorer.QualityReport quality = scorer.score(def, validation, null);
        assertThat(quality.components()).filteredOn(p -> p.name().equals("Validity")).first()
                .extracting(QualityScorer.Part::score).isEqualTo(0);
        assertThat(quality.meetsPublishThreshold()).isFalse();
    }

    @Test
    void qualityComponentsNeverExceedTheirMaximumAndSumToTheTotal() {
        ScenarioDefinition def = TestDefinitions.seed("01-ssh-bruteforce-vm");
        ValidationReport validation = analyzer.analyze(def);
        QualityScorer.QualityReport quality = scorer.score(def, validation, runner.run(def));
        assertThat(quality.components()).allMatch(p -> p.score() >= 0 && p.score() <= p.max());
        assertThat(quality.components().stream().mapToInt(QualityScorer.Part::max).sum()).isEqualTo(100);
        assertThat(quality.components().stream().mapToInt(QualityScorer.Part::score).sum()).isEqualTo(quality.score());
    }

    @Test
    void scoringIsDeterministic() {
        ScenarioDefinition def = TestDefinitions.seed("03-public-storage-bucket");
        var v = analyzer.analyze(def);
        var t = runner.run(def);
        assertThat(scorer.score(def, v, t)).isEqualTo(scorer.score(def, v, t));
    }
}
