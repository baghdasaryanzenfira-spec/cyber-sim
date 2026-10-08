package am.cybersim.authoring;

import am.cybersim.authoring.QualityScorer.QualityReport;
import am.cybersim.authoring.ScenarioAnalyzer.ValidationReport;
import am.cybersim.authoring.ScenarioGeneratorService.GenerateRequest;
import am.cybersim.authoring.ScenarioGeneratorService.Generated;
import am.cybersim.authoring.ScenarioTestRunner.TestRunReport;
import am.cybersim.common.ApiException;
import am.cybersim.scenario.Scenario;
import am.cybersim.scenario.ScenarioEnums.ScenarioStatus;
import am.cybersim.scenario.ScenarioMapper;
import am.cybersim.scenario.ScenarioService;
import am.cybersim.scenario.ScenarioVersion;
import am.cybersim.scenario.ScenarioVersionRepository;
import am.cybersim.scenario.dto.ScenarioDefinition;
import am.cybersim.scenario.dto.ScenarioDtos.AdminScenarioDetail;
import am.cybersim.scenario.dto.ScenarioDtos.AdminScenarioSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * The scenario authoring workflow, end to end:
 * <pre>
 *   generate draft -&gt; edit -&gt; graph -&gt; validate -&gt; test paths -&gt; quality score -&gt; publish new version
 * </pre>
 * {@link #evaluate} runs the analysis steps on a definition without storing anything; {@link #publish} repeats
 * it on the stored draft and only creates a version if every gate passes.
 */
@Service
public class AuthoringService {

    private static final Logger log = LoggerFactory.getLogger(AuthoringService.class);

    /** Result of validation + test runner + quality score for one definition. */
    public record Evaluation(ValidationReport validation, TestRunReport tests, QualityReport quality,
                             boolean publishable, List<String> blockers) {
    }

    public record GeneratedScenario(AdminScenarioDetail scenario, String aiSource) {
    }

    public record VersionSummary(Long id, int versionNumber, int qualityScore, String grade, String changeNote,
                                 Long publishedBy, java.time.Instant publishedAt) {
    }

    public record VersionDetail(VersionSummary summary, ScenarioDefinition definition, QualityReport quality) {
    }

    private final ScenarioService scenarioService;
    private final ScenarioMapper mapper;
    private final ScenarioAnalyzer analyzer;
    private final ScenarioTestRunner testRunner;
    private final QualityScorer qualityScorer;
    private final ScenarioGeneratorService generator;
    private final ScenarioVersionRepository versions;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final TransactionTemplate tx;

    public AuthoringService(ScenarioService scenarioService, ScenarioMapper mapper, ScenarioAnalyzer analyzer,
                            ScenarioTestRunner testRunner, QualityScorer qualityScorer,
                            ScenarioGeneratorService generator, ScenarioVersionRepository versions,
                            ObjectMapper objectMapper, Clock clock, PlatformTransactionManager transactionManager) {
        this.scenarioService = scenarioService;
        this.mapper = mapper;
        this.analyzer = analyzer;
        this.testRunner = testRunner;
        this.qualityScorer = qualityScorer;
        this.generator = generator;
        this.versions = versions;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactionManager);
    }

    // ------------------------------------------------------------------ scenario CRUD
    // Mapping happens inside the transaction: open-in-view is disabled, so lazy children must be read before it ends.

    @Transactional(readOnly = true)
    public List<AdminScenarioSummary> list() {
        return scenarioService.listAll().stream().map(mapper::toAdminSummary).toList();
    }

    @Transactional(readOnly = true)
    public AdminScenarioDetail detail(Long id) {
        return mapper.toAdminDetail(scenarioService.get(id));
    }

    @Transactional
    public AdminScenarioDetail create(ScenarioDefinition definition) {
        return mapper.toAdminDetail(scenarioService.create(definition));
    }

    @Transactional
    public AdminScenarioDetail update(Long id, ScenarioDefinition definition) {
        return mapper.toAdminDetail(scenarioService.update(id, definition));
    }

    @Transactional
    public AdminScenarioDetail setArchived(Long id, boolean archived) {
        return mapper.toAdminDetail(scenarioService.setArchived(id, archived));
    }

    @Transactional
    public void delete(Long id) {
        scenarioService.delete(id);
    }

    // ------------------------------------------------------------------ generate

    /** Generation (and the optional AI call) happens outside a transaction; only the save is transactional. */
    public GeneratedScenario generate(Long adminId, GenerateRequest request) {
        Generated generated = generator.generate(adminId, request);
        AdminScenarioDetail saved = store(generated.definition());
        log.info("Admin {} generated draft '{}' from template {}", adminId, saved.definition().slug(), request.type());
        return new GeneratedScenario(saved, generated.aiSource() == null ? null : generated.aiSource().name());
    }

    private AdminScenarioDetail store(ScenarioDefinition definition) {
        return tx.execute(status -> mapper.toAdminDetail(scenarioService.create(definition)));
    }

    // ------------------------------------------------------------------ analysis

    public Evaluation evaluate(ScenarioDefinition definition) {
        ValidationReport validation = analyzer.analyze(definition);
        TestRunReport tests = validation.valid() ? testRunner.run(definition) : null;
        QualityReport quality = qualityScorer.score(definition, validation, tests);

        List<String> blockers = new ArrayList<>();
        if (!validation.valid()) {
            blockers.add("Validation has " + validation.errorCount() + " error(s)");
        }
        if (tests != null && !tests.passed()) {
            tests.paths().stream().filter(p -> !p.passed())
                    .forEach(p -> blockers.add("The " + p.path().toLowerCase() + " path test fails"));
        }
        if (quality.score() < QualityScorer.PUBLISH_THRESHOLD) {
            blockers.add("Quality score " + quality.score() + " is below the publishing threshold of "
                    + QualityScorer.PUBLISH_THRESHOLD);
        }
        return new Evaluation(validation, tests, quality, blockers.isEmpty(), List.copyOf(blockers));
    }

    @Transactional(readOnly = true)
    public Evaluation evaluate(Long scenarioId) {
        return evaluate(mapper.toDefinition(scenarioService.get(scenarioId)));
    }

    @Transactional(readOnly = true)
    public ScenarioGraph graph(Long scenarioId) {
        return ScenarioGraph.build(mapper.toDefinition(scenarioService.get(scenarioId)));
    }

    // ------------------------------------------------------------------ publish / versions

    /**
     * Freezes the current draft as the next version. The gates are re-evaluated here on the stored draft, so a
     * client cannot publish something the analysis would reject.
     *
     * @throws ApiException 422 {@code PUBLISH_BLOCKED} listing the blockers
     */
    @Transactional
    public AdminScenarioDetail publish(Long adminId, Long scenarioId, String changeNote) {
        Scenario scenario = scenarioService.get(scenarioId);
        if (scenario.getStatus() == ScenarioStatus.ARCHIVED) {
            throw ApiException.conflict("SCENARIO_ARCHIVED", "Archived scenarios cannot be published; restore it first");
        }
        ScenarioDefinition definition = mapper.toDefinition(scenario);
        Evaluation evaluation = evaluate(definition);
        if (!evaluation.publishable()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "PUBLISH_BLOCKED",
                    "The scenario cannot be published: " + String.join("; ", evaluation.blockers()));
        }
        int next = scenario.getPublishedVersion() == null ? 1 : scenario.getPublishedVersion() + 1;
        try {
            versions.save(new ScenarioVersion(scenario.getId(), next, objectMapper.writeValueAsString(definition),
                    evaluation.quality().score(), objectMapper.writeValueAsString(evaluation.quality()),
                    blankToNull(changeNote), adminId, clock.instant()));
        } catch (JacksonException e) {
            throw new IllegalStateException("Scenario could not be serialised", e);
        }
        scenarioService.markPublished(scenario, next);
        log.info("Admin {} published scenario '{}' as version {} (quality {})", adminId, scenario.getSlug(), next,
                evaluation.quality().score());
        return mapper.toAdminDetail(scenario);
    }

    @Transactional(readOnly = true)
    public List<VersionSummary> versions(Long scenarioId) {
        scenarioService.get(scenarioId);
        return versions.findByScenarioIdOrderByVersionNumberDesc(scenarioId).stream().map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public VersionDetail version(Long scenarioId, int versionNumber) {
        ScenarioVersion v = versions.findByScenarioIdAndVersionNumber(scenarioId, versionNumber)
                .orElseThrow(() -> ApiException.notFound("Version"));
        try {
            return new VersionDetail(summary(v), objectMapper.readValue(v.getDefinition(), ScenarioDefinition.class),
                    objectMapper.readValue(v.getQuality(), QualityReport.class));
        } catch (JacksonException e) {
            throw new IllegalStateException("Stored version could not be read", e);
        }
    }

    /** Loads a published version back into the editable draft (e.g. after bad edits). */
    @Transactional
    public AdminScenarioDetail restoreVersion(Long scenarioId, int versionNumber) {
        VersionDetail v = version(scenarioId, versionNumber);
        return mapper.toAdminDetail(scenarioService.update(scenarioId, v.definition()));
    }

    private VersionSummary summary(ScenarioVersion v) {
        return new VersionSummary(v.getId(), v.getVersionNumber(), v.getQualityScore(),
                QualityScorer.grade(v.getQualityScore()), v.getChangeNote(), v.getPublishedBy(), v.getPublishedAt());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
