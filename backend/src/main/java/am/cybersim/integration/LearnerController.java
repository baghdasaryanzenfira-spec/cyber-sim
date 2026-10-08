package am.cybersim.integration;

import am.cybersim.common.ApiException;
import am.cybersim.integration.dto.IntegrationDtos.AttemptReceipt;
import am.cybersim.integration.dto.IntegrationDtos.PublishedScenarioDetail;
import am.cybersim.integration.dto.IntegrationDtos.PublishedScenarioSummary;
import am.cybersim.integration.dto.IntegrationDtos.SubmitAttemptRequest;
import am.cybersim.scenario.Scenario;
import am.cybersim.scenario.ScenarioRepository;
import am.cybersim.scenario.ScenarioVersionRepository;
import am.cybersim.scenario.dto.ScenarioDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import java.util.Comparator;
import java.util.List;

/**
 * Service API for the learner module (ADR-13). Authenticated with the {@code X-API-Key} header
 * ({@code LEARNER_API_KEY}), not with user JWTs — the learner module is a system, not a person.
 * It sees only published content and its own submitted attempts; it can never touch drafts or authoring.
 */
@RestController
@RequestMapping("/api/learner")
@Tag(name = "Learner module integration")
public class LearnerController {

    private final ScenarioRepository scenarios;
    private final ScenarioVersionRepository versions;
    private final AttemptService attemptService;
    private final ObjectMapper objectMapper;

    public LearnerController(ScenarioRepository scenarios, ScenarioVersionRepository versions,
                             AttemptService attemptService, ObjectMapper objectMapper) {
        this.scenarios = scenarios;
        this.versions = versions;
        this.attemptService = attemptService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/scenarios")
    @Transactional(readOnly = true)
    @Operation(summary = "All scenarios that have a published version (the catalogue for trainees)")
    public List<PublishedScenarioSummary> scenarios() {
        return scenarios.findAll().stream()
                .filter(s -> s.getPublishedVersion() != null)
                .sorted(Comparator.comparing(Scenario::getSlug))
                .map(this::summary)
                .toList();
    }

    @GetMapping("/scenarios/{slug}")
    @Transactional(readOnly = true)
    @Operation(summary = "The published definition of one scenario (latest published version)")
    public PublishedScenarioDetail scenario(@PathVariable String slug) {
        Scenario scenario = scenarios.findBySlug(slug)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SCENARIO_NOT_FOUND",
                        "No scenario with slug '" + slug + "'"));
        if (scenario.getPublishedVersion() == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SCENARIO_NOT_PUBLISHED",
                    "Scenario '" + slug + "' has no published version");
        }
        var version = versions.findByScenarioIdAndVersionNumber(scenario.getId(), scenario.getPublishedVersion())
                .orElseThrow();
        return new PublishedScenarioDetail(slug, version.getVersionNumber(), version.getQualityScore(),
                objectMapper.readValue(version.getDefinition(), ScenarioDefinition.class));
    }

    @PostMapping("/attempts")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Submit a completed attempt; the response carries this platform's verified score")
    public AttemptReceipt submit(@Valid @RequestBody SubmitAttemptRequest request) {
        return attemptService.submit(request);
    }

    @GetMapping("/attempts/{externalId}")
    @Operation(summary = "A submitted attempt: verified result plus the AI review once an administrator ran it")
    public AttemptReceipt attempt(@PathVariable String externalId) {
        return attemptService.receiptByExternalId(externalId);
    }

    private PublishedScenarioSummary summary(Scenario s) {
        var version = versions.findByScenarioIdAndVersionNumber(s.getId(), s.getPublishedVersion()).orElseThrow();
        ScenarioDefinition definition = objectMapper.readValue(version.getDefinition(), ScenarioDefinition.class);
        int maxScore = definition.actions().stream()
                .filter(a -> a.outcome() == am.cybersim.scenario.ScenarioEnums.ActionOutcome.EXPECTED)
                .mapToInt(ScenarioDefinition.ActionDef::points).sum();
        return new PublishedScenarioSummary(s.getSlug(), definition.title(), definition.summary(),
                definition.difficulty(), definition.category(), definition.estimatedMinutes(),
                version.getVersionNumber(), version.getQualityScore(), maxScore);
    }
}
