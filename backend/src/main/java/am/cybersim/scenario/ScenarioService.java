package am.cybersim.scenario;

import am.cybersim.common.ApiException;
import am.cybersim.scenario.dto.ScenarioDefinition;
import am.cybersim.scenario.dto.ScenarioDtos.ScenarioBriefing;
import am.cybersim.scenario.dto.ScenarioDtos.ScenarioSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/**
 * Scenario catalogue (students) and scenario authoring (admins).
 * Every write goes through {@link ScenarioDefinitionValidator}.
 */
@Service
public class ScenarioService {

    private static final Logger log = LoggerFactory.getLogger(ScenarioService.class);

    private final ScenarioRepository scenarios;
    private final ScenarioMapper mapper;
    private final ScenarioDefinitionValidator validator;
    private final Clock clock;

    public ScenarioService(ScenarioRepository scenarios, ScenarioMapper mapper, ScenarioDefinitionValidator validator,
                           Clock clock) {
        this.scenarios = scenarios;
        this.mapper = mapper;
        this.validator = validator;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- student catalogue

    @Transactional(readOnly = true)
    public List<ScenarioSummary> listActive() {
        return scenarios.findByActiveTrueOrderByDifficultyAscTitleAsc().stream().map(mapper::toSummary).toList();
    }

    /** Inactive scenarios are reported as "not found" to students. */
    @Transactional(readOnly = true)
    public ScenarioBriefing getBriefing(Long id) {
        return mapper.toBriefing(getActive(id));
    }

    @Transactional(readOnly = true)
    public Scenario getActive(Long id) {
        return scenarios.findById(id).filter(Scenario::isActive)
                .orElseThrow(() -> ApiException.notFound("Scenario"));
    }

    // ---------------------------------------------------------------- authoring

    @Transactional(readOnly = true)
    public Scenario get(Long id) {
        return scenarios.findById(id).orElseThrow(() -> ApiException.notFound("Scenario"));
    }

    @Transactional(readOnly = true)
    public List<Scenario> listAll() {
        return scenarios.findAllByOrderByCreatedAtAsc();
    }

    @Transactional
    public Scenario create(ScenarioDefinition definition) {
        return create(definition, null);
    }

    /**
     * @param sourceScenarioId the original scenario when the definition is an (AI-generated) variation
     */
    @Transactional
    public Scenario create(ScenarioDefinition definition, Long sourceScenarioId) {
        validator.validate(definition);
        if (scenarios.existsBySlug(definition.slug())) {
            throw ApiException.conflict("SLUG_TAKEN", "A scenario with slug '" + definition.slug() + "' already exists");
        }
        Scenario scenario = new Scenario(definition.slug(), clock.instant());
        scenario.setActive(definition.active());
        scenario.setSourceScenarioId(sourceScenarioId);
        mapper.applyDefinition(scenario, definition);
        scenarios.save(scenario);
        log.info("Created scenario '{}' (id={}, active={})", scenario.getSlug(), scenario.getId(), scenario.isActive());
        return scenario;
    }

    /**
     * Replaces the full content of a scenario and increments its version. Running simulations keep working:
     * they reference actions by key and snapshot points at the time each action is performed.
     */
    @Transactional
    public Scenario update(Long id, ScenarioDefinition definition) {
        validator.validate(definition);
        Scenario scenario = get(id);
        if (!scenario.getSlug().equals(definition.slug())) {
            throw ApiException.badRequest("SLUG_IMMUTABLE", "The slug of an existing scenario cannot be changed");
        }
        scenario.clearChildren();
        // Hibernate executes inserts before orphan deletes; flushing first avoids unique-key collisions
        // between the removed and the re-added children.
        scenarios.flush();
        mapper.applyDefinition(scenario, definition);
        scenario.setActive(definition.active());
        scenario.incrementVersion();
        scenario.touch(clock.instant());
        log.info("Updated scenario '{}' to version {}", scenario.getSlug(), scenario.getVersion());
        return scenario;
    }

    @Transactional
    public Scenario setActive(Long id, boolean active) {
        Scenario scenario = get(id);
        scenario.setActive(active);
        scenario.touch(clock.instant());
        log.info("Scenario '{}' active={}", scenario.getSlug(), active);
        return scenario;
    }

    @Transactional(readOnly = true)
    public boolean existsBySlug(String slug) {
        return scenarios.existsBySlug(slug);
    }

    @Transactional(readOnly = true)
    public long countSlugsStartingWith(String prefix) {
        return scenarios.countBySlugStartingWith(prefix);
    }
}
