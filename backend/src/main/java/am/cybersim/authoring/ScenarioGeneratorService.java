package am.cybersim.authoring;

import am.cybersim.ai.AiGateway;
import am.cybersim.ai.AiGateway.AiResult;
import am.cybersim.ai.AiOutputValidator;
import am.cybersim.ai.AiPayload;
import am.cybersim.ai.AiProviderException;
import am.cybersim.ai.ScenarioVariationService;
import am.cybersim.ai.dto.AiDtos.AiSource;
import am.cybersim.common.ApiException;
import am.cybersim.scenario.ScenarioDefinitionValidator;
import am.cybersim.scenario.ScenarioEnums.Difficulty;
import am.cybersim.scenario.ScenarioService;
import am.cybersim.scenario.dto.ScenarioDefinition;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Function;

/**
 * Scenario generator (authoring step "the service generates timeline, commands, evidence and actions").
 *
 * <p>A scenario <b>type</b> maps to a vetted template (complete timeline, log lines/commands, evidence markers
 * and scored response actions). The generator instantiates the template with the admin's parameters
 * (primary asset, attacker IP, region, title, difficulty) and stores the result as a DRAFT. Optionally the
 * narrative is polished by the AI provider ("AI proposes, application decides": the structure must stay
 * identical and the result passes the normal validator, otherwise the deterministic draft is kept).
 */
@Service
public class ScenarioGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(ScenarioGeneratorService.class);

    public enum ScenarioType {
        SSH_BRUTE_FORCE("scenarios/01-ssh-bruteforce-vm.json", "web-prod-01", "203.0.113.45", "eu-central-1"),
        COMPROMISED_CREDENTIALS("scenarios/02-compromised-credentials.json", "maria.k", "102.89.33.17", "eu-west-1"),
        PUBLIC_STORAGE_BUCKET("scenarios/03-public-storage-bucket.json", "acme-customer-exports", "185.220.101.4",
                "eu-west-1");

        private final String template;
        private final String defaultAsset;
        private final String defaultAttackerIp;
        private final String defaultRegion;

        ScenarioType(String template, String defaultAsset, String defaultAttackerIp, String defaultRegion) {
            this.template = template;
            this.defaultAsset = defaultAsset;
            this.defaultAttackerIp = defaultAttackerIp;
            this.defaultRegion = defaultRegion;
        }
    }

    /** All fields except {@code type} are optional; missing values keep the template's defaults. */
    public record GenerateRequest(
            @jakarta.validation.constraints.NotNull ScenarioType type,
            @Size(max = 200) String title,
            Difficulty difficulty,
            @Size(max = 100) @Pattern(regexp = "^[A-Za-z0-9._-]*$", message = "letters, digits, dot, dash, underscore") String primaryAsset,
            @Pattern(regexp = "^(|(\\d{1,3}\\.){3}\\d{1,3})$", message = "must be an IPv4 address") String attackerIp,
            @Size(max = 40) @Pattern(regexp = "^[a-z0-9-]*$", message = "lower-case letters, digits, dashes") String region,
            @Size(max = 1000) String brief,
            Boolean useAi) {
    }

    public record Generated(ScenarioDefinition definition, AiSource aiSource) {
    }

    private final ObjectMapper objectMapper;
    private final ScenarioService scenarioService;
    private final ScenarioDefinitionValidator validator;
    private final AiGateway gateway;
    private final AiOutputValidator outputValidator;

    public ScenarioGeneratorService(ObjectMapper objectMapper, ScenarioService scenarioService,
                                    ScenarioDefinitionValidator validator, AiGateway gateway,
                                    AiOutputValidator outputValidator) {
        this.objectMapper = objectMapper;
        this.scenarioService = scenarioService;
        this.validator = validator;
        this.gateway = gateway;
        this.outputValidator = outputValidator;
    }

    /** Builds (but does not store) a validated definition. {@code aiSource} is null when AI was not requested. */
    public Generated generate(Long adminId, GenerateRequest request) {
        ScenarioDefinition template = loadTemplate(request.type());
        ScenarioDefinition instance = instantiate(template, request);
        validator.validate(instance);
        if (!Boolean.TRUE.equals(request.useAi())) {
            return new Generated(instance, null);
        }
        Function<String, ScenarioDefinition> check = json -> {
            ScenarioDefinition candidate = outputValidator.parseScenarioDefinition(json);
            ScenarioDefinition normalized = withSlug(candidate, instance.slug());
            ScenarioVariationService.requireSameStructure(instance, normalized);
            validator.validate(normalized);
            return normalized;
        };
        try {
            AiResult<ScenarioDefinition> result = gateway.execute(
                    new AiPayload.Generation(instance, request.brief() == null ? "" : request.brief()),
                    new AiGateway.CallContext(adminId, null, "Generate " + request.type()), check);
            return new Generated(result.value(), result.source());
        } catch (ApiException | AiProviderException e) {
            log.warn("AI enrichment rejected, keeping the template draft: {}", e.getMessage());
            return new Generated(instance, AiSource.FALLBACK);
        }
    }

    private ScenarioDefinition instantiate(ScenarioDefinition template, GenerateRequest r) {
        ScenarioType type = r.type();
        String json = objectMapper.writeValueAsString(template);
        json = swap(json, type.defaultAsset, r.primaryAsset());
        json = swap(json, type.defaultAttackerIp, r.attackerIp());
        json = swap(json, type.defaultRegion, r.region());
        ScenarioDefinition copy = objectMapper.readValue(json, ScenarioDefinition.class);

        String title = present(r.title()) ? r.title().strip() : copy.title();
        String slug = uniqueSlug(title);
        Difficulty difficulty = r.difficulty() != null ? r.difficulty() : copy.difficulty();
        return new ScenarioDefinition(slug, title, copy.summary(), copy.description(), difficulty, copy.category(),
                copy.estimatedMinutes(), copy.incidentExplanation(), copy.recommendedSolution(), copy.hintPenalty(),
                copy.outOfOrderPenalty(), copy.learningObjectives(), copy.resources(), copy.events(), copy.actions(),
                copy.hints());
    }

    private ScenarioDefinition loadTemplate(ScenarioType type) {
        try (InputStream in = new ClassPathResource(type.template).getInputStream()) {
            return objectMapper.readValue(in, ScenarioDefinition.class);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "TEMPLATE_UNAVAILABLE",
                    "Template for " + type + " could not be loaded");
        }
    }

    private String uniqueSlug(String title) {
        String base = Normalizer.normalize(title, Normalizer.Form.NFD).replaceAll("[^\\p{ASCII}]", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (base.length() < 2) {
            base = "scenario";
        }
        base = base.length() > 80 ? base.substring(0, 80).replaceAll("-+$", "") : base;
        String slug = base;
        int n = 1;
        while (scenarioService.existsBySlug(slug)) {
            slug = base + "-" + (++n);
        }
        return slug;
    }

    private static ScenarioDefinition withSlug(ScenarioDefinition d, String slug) {
        return new ScenarioDefinition(slug, d.title(), d.summary(), d.description(), d.difficulty(), d.category(),
                d.estimatedMinutes(), d.incidentExplanation(), d.recommendedSolution(), d.hintPenalty(),
                d.outOfOrderPenalty(), d.learningObjectives(), d.resources(), d.events(), d.actions(), d.hints());
    }

    private static String swap(String text, String from, String to) {
        return present(to) ? text.replace(from, to.strip()) : text;
    }

    private static boolean present(String s) {
        return s != null && !s.isBlank();
    }
}
