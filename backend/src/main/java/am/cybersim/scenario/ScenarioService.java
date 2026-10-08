package am.cybersim.scenario;

import am.cybersim.common.ApiException;
import am.cybersim.scenario.dto.ScenarioDefinition;
import am.cybersim.scenario.ScenarioEnums.ScenarioStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/**
 * Scenario authoring: create, edit and manage the lifecycle of scenarios.
 * Every write goes through {@link ScenarioDefinitionValidator}. Publishing (which also needs the quality gate)
 * is orchestrated by {@code AuthoringService}.
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
        scenario.setSourceScenarioId(sourceScenarioId);
        mapper.applyDefinition(scenario, definition);
        scenarios.save(scenario);
        log.info("Created draft scenario '{}' (id={})", scenario.getSlug(), scenario.getId());
        return scenario;
    }

    /**
     * Replaces the full content of the working copy and increments its revision. Editing a published scenario
     * turns it back into a DRAFT; its last published version stays untouched and available.
     */
    @Transactional
    public Scenario update(Long id, ScenarioDefinition definition) {
        validator.validate(definition);
        Scenario scenario = get(id);
        if (scenario.getStatus() == ScenarioStatus.ARCHIVED) {
            throw ApiException.conflict("SCENARIO_ARCHIVED", "Archived scenarios cannot be edited; restore it first");
        }
        if (!scenario.getSlug().equals(definition.slug())) {
            throw ApiException.badRequest("SLUG_IMMUTABLE", "The slug of an existing scenario cannot be changed");
        }
        scenario.clearChildren();
        // Hibernate executes inserts before orphan deletes; flushing first avoids unique-key collisions
        // between the removed and the re-added children.
        scenarios.flush();
        mapper.applyDefinition(scenario, definition);
        scenario.setStatus(ScenarioStatus.DRAFT);
        scenario.incrementRevision();
        scenario.touch(clock.instant());
        log.info("Updated scenario '{}' to revision {}", scenario.getSlug(), scenario.getRevision());
        return scenario;
    }

    /** Records that the current working copy was published as {@code versionNumber}. */
    @Transactional
    public void markPublished(Scenario scenario, int versionNumber) {
        scenario.setStatus(ScenarioStatus.PUBLISHED);
        scenario.setPublishedVersion(versionNumber);
        scenario.touch(clock.instant());
    }

    /** ARCHIVED hides the scenario from consumers; restoring makes it an editable DRAFT again. */
    @Transactional
    public Scenario setArchived(Long id, boolean archived) {
        Scenario scenario = get(id);
        scenario.setStatus(archived ? ScenarioStatus.ARCHIVED : ScenarioStatus.DRAFT);
        scenario.touch(clock.instant());
        log.info("Scenario '{}' archived={}", scenario.getSlug(), archived);
        return scenario;
    }

    /** Only never-published scenarios can be deleted; published ones are archived to keep their versions. */
    @Transactional
    public void delete(Long id) {
        Scenario scenario = get(id);
        if (scenario.getPublishedVersion() != null) {
            throw ApiException.conflict("SCENARIO_PUBLISHED",
                    "A published scenario cannot be deleted; archive it instead");
        }
        scenarios.delete(scenario);
        log.info("Deleted draft scenario '{}'", scenario.getSlug());
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
