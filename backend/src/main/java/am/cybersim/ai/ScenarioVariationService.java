package am.cybersim.ai;

import am.cybersim.ai.AiGateway.AiResult;
import am.cybersim.ai.dto.AiDtos.AiSource;
import am.cybersim.common.ApiException;
import am.cybersim.scenario.Scenario;
import am.cybersim.scenario.ScenarioDefinitionValidator;
import am.cybersim.scenario.ScenarioMapper;
import am.cybersim.scenario.ScenarioService;
import am.cybersim.scenario.dto.ScenarioDefinition;
import am.cybersim.scenario.dto.ScenarioDefinition.ActionDef;
import am.cybersim.scenario.dto.ScenarioDefinition.EventDef;
import am.cybersim.scenario.dto.ScenarioDefinition.ResourceDef;
import am.cybersim.scenario.dto.ScenarioDtos.AdminScenarioDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * C. AI scenario variation (requirement §8.C) — "AI proposes, application decides".
 *
 * <p>Pipeline: original definition → AI → JSON parse → <b>structural equivalence check</b> (same keys, phases,
 * outcomes, points, evidence markers, references as the original) → standard {@link ScenarioDefinitionValidator}
 * → stored as a <b>draft</b> with a link to the original. An administrator must review and publish it.
 * Because scoring-relevant structure cannot change, the deterministic engine and scoring stay valid.
 */
@Service
public class ScenarioVariationService {

    private static final Logger log = LoggerFactory.getLogger(ScenarioVariationService.class);

    private final ScenarioService scenarioService;
    private final ScenarioMapper scenarioMapper;
    private final ScenarioDefinitionValidator definitionValidator;
    private final AiGateway gateway;
    private final AiOutputValidator outputValidator;
    private final TransactionTemplate tx;

    public ScenarioVariationService(ScenarioService scenarioService, ScenarioMapper scenarioMapper,
                                    ScenarioDefinitionValidator definitionValidator, AiGateway gateway,
                                    AiOutputValidator outputValidator, PlatformTransactionManager transactionManager) {
        this.scenarioService = scenarioService;
        this.scenarioMapper = scenarioMapper;
        this.definitionValidator = definitionValidator;
        this.gateway = gateway;
        this.outputValidator = outputValidator;
        this.tx = new TransactionTemplate(transactionManager);
    }

    public record VariationResult(AdminScenarioDetail scenario, AiSource source) {
    }

    public VariationResult generate(Long adminId, Long scenarioId) {
        record Source(ScenarioDefinition definition, String newSlug) {
        }
        Source source = Objects.requireNonNull(tx.execute(status -> {
            Scenario original = scenarioService.get(scenarioId);
            String prefix = truncate(original.getSlug(), 88) + "-var-";
            String slug = prefix + (scenarioService.countSlugsStartingWith(prefix) + 1);
            return new Source(scenarioMapper.toDefinition(original), slug);
        }));

        Function<String, ScenarioDefinition> validate = json -> {
            ScenarioDefinition candidate = outputValidator.parseScenarioDefinition(json);
            ScenarioDefinition normalized = withSlug(candidate, source.newSlug());
            requireSameStructure(source.definition(), normalized);
            definitionValidator.validate(normalized);
            return normalized;
        };

        AiResult<ScenarioDefinition> result;
        try {
            result = gateway.execute(new AiPayload.Variation(source.definition(), source.newSlug()),
                    new AiGateway.CallContext(adminId, scenarioId, "Variation of scenario " + scenarioId),
                    validate);
        } catch (ApiException | AiProviderException e) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_VARIATION",
                    "The generated variation did not pass validation: " + e.getMessage());
        }

        AdminScenarioDetail saved = tx.execute(status ->
                scenarioMapper.toAdminDetail(scenarioService.create(result.value(), scenarioId)));
        log.info("Admin {} generated variation '{}' of scenario {} (source {})", adminId, source.newSlug(),
                scenarioId, result.source());
        return new VariationResult(saved, result.source());
    }

    /**
     * Rejects any variation that changes scoring-relevant structure.
     * @throws AiProviderException so that the gateway treats the AI output as invalid and falls back
     */
    public static void requireSameStructure(ScenarioDefinition original, ScenarioDefinition variant) {
        check(original.difficulty() == variant.difficulty() && original.category() == variant.category()
                && original.hintPenalty() == variant.hintPenalty()
                && original.outOfOrderPenalty() == variant.outOfOrderPenalty(), "scenario settings changed");
        check(original.resources().size() == variant.resources().size(), "resource count changed");
        for (int i = 0; i < original.resources().size(); i++) {
            ResourceDef a = original.resources().get(i);
            ResourceDef b = variant.resources().get(i);
            check(a.key().equals(b.key()) && a.type() == b.type(), "resource " + a.key() + " changed");
        }
        check(original.events().size() == variant.events().size(), "event count changed");
        for (int i = 0; i < original.events().size(); i++) {
            EventDef a = original.events().get(i);
            EventDef b = variant.events().get(i);
            check(a.key().equals(b.key()) && a.type() == b.type() && a.evidence() == b.evidence()
                    && Objects.equals(a.revealedByActionKey(), b.revealedByActionKey())
                    && Objects.equals(a.resourceKey(), b.resourceKey()), "event " + a.key() + " changed");
        }
        check(original.actions().size() == variant.actions().size(), "action count changed");
        for (int i = 0; i < original.actions().size(); i++) {
            ActionDef a = original.actions().get(i);
            ActionDef b = variant.actions().get(i);
            check(a.key().equals(b.key()) && a.phase() == b.phase() && a.category() == b.category()
                    && a.outcome() == b.outcome() && a.points() == b.points()
                    && Objects.equals(a.prerequisiteActionKey(), b.prerequisiteActionKey())
                    && Objects.equals(a.targetResourceKey(), b.targetResourceKey())
                    && Objects.equals(a.effectStatus(), b.effectStatus()), "action " + a.key() + " changed");
        }
    }

    private static ScenarioDefinition withSlug(ScenarioDefinition d, String slug) {
        return new ScenarioDefinition(slug, d.title(), d.summary(), d.description(), d.difficulty(), d.category(),
                d.estimatedMinutes(), d.incidentExplanation(), d.recommendedSolution(), d.hintPenalty(),
                d.outOfOrderPenalty(), d.learningObjectives(), d.resources(), d.events(), d.actions(),
                d.hints() == null ? List.of() : d.hints());
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AiProviderException("Variation rejected: " + message);
        }
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
