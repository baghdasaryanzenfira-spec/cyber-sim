package am.cybersim.integration;

import am.cybersim.config.AppProperties;
import am.cybersim.integration.dto.IntegrationDtos.SubmitAttemptRequest;
import am.cybersim.integration.dto.IntegrationDtos.SubmittedAction;
import am.cybersim.scenario.ScenarioRepository;
import am.cybersim.scenario.ScenarioVersionRepository;
import am.cybersim.scenario.dto.ScenarioDefinition;
import am.cybersim.scenario.dto.ScenarioDefinition.ActionDef;
import am.cybersim.scenario.ScenarioEnums.ActionOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Seeds two demonstration exam submissions (one strong, one weak with a false claimed score) so the admin
 * Exams page and the AI-review button can be shown without the learner module running. Uses the same public
 * submission path as the learner module, so what the demo shows is exactly what the integration does.
 * Runs after {@code ScenarioSeeder} (order 20) and only when demo data is enabled and no attempts exist.
 */
@Component
@Order(30)
public class DemoAttemptSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoAttemptSeeder.class);
    private static final String SLUG = "ssh-bruteforce-vm";

    private final AppProperties properties;
    private final StudentAttemptRepository attempts;
    private final ScenarioRepository scenarios;
    private final ScenarioVersionRepository versions;
    private final AttemptService attemptService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public DemoAttemptSeeder(AppProperties properties, StudentAttemptRepository attempts,
                             ScenarioRepository scenarios, ScenarioVersionRepository versions,
                             AttemptService attemptService, ObjectMapper objectMapper, Clock clock) {
        this.properties = properties;
        this.attempts = attempts;
        this.scenarios = scenarios;
        this.versions = versions;
        this.attemptService = attemptService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.demo().enabled() || attempts.count() > 0) {
            return;
        }
        var scenario = scenarios.findBySlug(SLUG).orElse(null);
        if (scenario == null || scenario.getPublishedVersion() == null) {
            return;
        }
        var version = versions.findByScenarioIdAndVersionNumber(scenario.getId(), scenario.getPublishedVersion())
                .orElseThrow();
        ScenarioDefinition definition = objectMapper.readValue(version.getDefinition(), ScenarioDefinition.class);

        Instant now = clock.instant();
        List<SubmittedAction> good = correctOrder(definition).stream()
                .map(a -> new SubmittedAction(a.key(), null)).toList();
        submit("demo-exam-001", "student-001", "Ani Petrosyan", good, 0, 100,
                now.minus(Duration.ofHours(5)), now.minus(Duration.ofHours(5)).plus(Duration.ofMinutes(21)));

        List<SubmittedAction> poor = new ArrayList<>();
        definition.actions().stream().filter(a -> a.outcome() == ActionOutcome.EXPECTED).limit(2)
                .forEach(a -> poor.add(new SubmittedAction(a.key(), null)));
        definition.actions().stream().filter(a -> a.outcome() == ActionOutcome.HARMFUL).limit(2)
                .forEach(a -> poor.add(new SubmittedAction(a.key(), null)));
        // the claimed score is deliberately wrong, so the dashboard demonstrates the mismatch flag
        submit("demo-exam-002", "student-002", "Vahe Sargsyan", poor, 3, 85,
                now.minus(Duration.ofHours(2)), now.minus(Duration.ofHours(2)).plus(Duration.ofMinutes(9)));
        log.info("Seeded 2 demo exam submissions for '{}'", SLUG);
    }

    private void submit(String externalId, String studentRef, String name, List<SubmittedAction> actions,
                        int hints, Integer claimed, Instant started, Instant completed) {
        attemptService.submit(new SubmitAttemptRequest(externalId, SLUG, null, studentRef, name,
                started, completed, hints, actions, claimed));
    }

    /** Expected actions with prerequisites satisfied first — the same order the test runner's correct path uses. */
    private static List<ActionDef> correctOrder(ScenarioDefinition definition) {
        List<ActionDef> expected = definition.actions().stream()
                .filter(a -> a.outcome() == ActionOutcome.EXPECTED).toList();
        List<ActionDef> ordered = new ArrayList<>();
        Set<String> placed = new HashSet<>();
        while (ordered.size() < expected.size()) {
            boolean progressed = false;
            for (ActionDef a : expected) {
                if (placed.contains(a.key())) {
                    continue;
                }
                String pre = a.prerequisiteActionKey();
                if (pre == null || pre.isBlank() || placed.contains(pre)
                        || expected.stream().noneMatch(e -> e.key().equals(pre))) {
                    ordered.add(a);
                    placed.add(a.key());
                    progressed = true;
                }
            }
            if (!progressed) {
                break; // defensive: a prerequisite cycle would already have failed validation at publish time
            }
        }
        return ordered;
    }
}
