package am.cybersim.authoring;

import am.cybersim.authoring.ScenarioAnalyzer.GraphStats;
import am.cybersim.authoring.ScenarioAnalyzer.ValidationReport;
import am.cybersim.authoring.ScenarioTestRunner.PathResult;
import am.cybersim.authoring.ScenarioTestRunner.TestRunReport;
import am.cybersim.scenario.dto.ScenarioDefinition;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Scenario quality score, 0-100 (authoring step "System calculates the scenario quality score").
 * Deterministic and explainable: the score is the sum of five components, each with its own maximum and a
 * human-readable justification, so an author knows exactly what to improve.
 *
 * <pre>
 *   validity   25  no errors; each warning costs 2.5
 *   tests      25  correct path 15 + dangerous path 10
 *   coverage   20  events, evidence, resources and incident-response categories
 *   pedagogy   20  objectives, hints, distractors, explanations
 *   structure  10  dependency depth and progressive disclosure of evidence
 * </pre>
 */
@Component
public class QualityScorer {

    /** Minimum score to publish a scenario. */
    public static final int PUBLISH_THRESHOLD = 70;

    public record Part(String name, int score, int max, String details) {
    }

    public record QualityReport(int score, String grade, boolean meetsPublishThreshold, List<Part> components) {
    }

    public QualityReport score(ScenarioDefinition def, ValidationReport validation, TestRunReport tests) {
        List<Part> parts = new ArrayList<>();
        parts.add(validity(validation));
        parts.add(tests(tests));
        parts.add(coverage(def, validation.stats()));
        parts.add(pedagogy(def, validation.stats()));
        parts.add(structure(def, validation.stats()));
        int total = parts.stream().mapToInt(Part::score).sum();
        return new QualityReport(total, grade(total), total >= PUBLISH_THRESHOLD, List.copyOf(parts));
    }

    static String grade(int score) {
        if (score >= 85) {
            return "EXCELLENT";
        }
        if (score >= PUBLISH_THRESHOLD) {
            return "GOOD";
        }
        return score >= 50 ? "FAIR" : "POOR";
    }

    private Part validity(ValidationReport v) {
        if (!v.valid()) {
            return new Part("Validity", 0, 25, v.errorCount() + " error(s) - fix them first");
        }
        int score = Math.max(0, 25 - Math.round(v.warningCount() * 2.5f));
        return new Part("Validity", score, 25, v.warningCount() == 0 ? "no errors, no warnings"
                : "no errors, " + v.warningCount() + " warning(s)");
    }

    private Part tests(TestRunReport t) {
        if (t == null) {
            return new Part("Tests", 0, 25, "not run while the scenario has errors");
        }
        int score = 0;
        List<String> notes = new ArrayList<>();
        for (PathResult p : t.paths()) {
            int max = "CORRECT".equals(p.path()) ? 15 : 10;
            long ok = p.checks().stream().filter(ScenarioTestRunner.Check::passed).count();
            int partial = Math.round(max * (float) ok / Math.max(1, p.checks().size()));
            score += p.passed() ? max : Math.min(partial, max - 1);
            notes.add(p.path().toLowerCase() + " path " + ok + "/" + p.checks().size() + " checks");
        }
        return new Part("Tests", score, 25, String.join(", ", notes));
    }

    private Part coverage(ScenarioDefinition def, GraphStats s) {
        if (s == null) {
            return new Part("Coverage", 0, 20, "not available while the scenario has errors");
        }
        int score = ratio(def.events().size(), 8, 5) + ratio(s.evidenceEvents(), 3, 5)
                + ratio(def.resources().size(), 3, 4) + ratio(s.categoriesCovered(), 4, 6);
        return new Part("Coverage", score, 20, def.events().size() + " events, " + s.evidenceEvents()
                + " evidence, " + def.resources().size() + " resources, " + s.categoriesCovered()
                + " response categories on the correct path");
    }

    private Part pedagogy(ScenarioDefinition def, GraphStats s) {
        if (s == null) {
            return new Part("Pedagogy", 0, 20, "not available while the scenario has errors");
        }
        double avgExplanation = def.actions().stream().mapToInt(a -> a.explanation().strip().length()).average().orElse(0);
        int score = ratio(def.learningObjectives().size(), 4, 4) + ratio(def.hints().size(), 3, 4)
                + ratio(s.harmfulActions(), 2, 4) + ratio(s.neutralActions(), 1, 3)
                + ratio((int) avgExplanation, 60, 5);
        return new Part("Pedagogy", score, 20, def.learningObjectives().size() + " objectives, "
                + def.hints().size() + " hints, " + s.harmfulActions() + " harmful and " + s.neutralActions()
                + " neutral actions, average explanation " + (int) avgExplanation + " characters");
    }

    private Part structure(ScenarioDefinition def, GraphStats s) {
        if (s == null) {
            return new Part("Structure", 0, 10, "not available while the scenario has errors");
        }
        long hidden = def.events().stream().filter(e -> !ScenarioGraph.blank(e.revealedByActionKey())).count();
        int hiddenPercent = def.events().isEmpty() ? 0 : (int) (hidden * 100 / def.events().size());
        long withPrereq = def.actions().stream().filter(a -> !ScenarioGraph.blank(a.prerequisiteActionKey())).count();
        int score = ratio(s.maxDepth(), 3, 5) + ratio(hiddenPercent, 40, 5);
        return new Part("Structure", score, 10, "longest prerequisite chain " + s.maxDepth() + ", "
                + withPrereq + " dependent actions, " + hiddenPercent + " % of events revealed progressively");
    }

    /** Linear score up to {@code target}: value >= target gives the full {@code weight}. */
    private static int ratio(int value, int target, int weight) {
        return Math.round(weight * Math.min(1f, value / (float) target));
    }
}
