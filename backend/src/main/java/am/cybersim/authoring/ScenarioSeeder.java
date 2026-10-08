package am.cybersim.authoring;

import am.cybersim.common.ApiException;
import am.cybersim.config.AppProperties;
import am.cybersim.scenario.Scenario;
import am.cybersim.scenario.ScenarioService;
import am.cybersim.scenario.dto.ScenarioDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Imports the demonstration scenarios from {@code classpath:scenarios/*.json} at start-up and publishes each as
 * version 1 (they double as templates for the generator). A seed that fails the publishing gate stays a draft.
 * Idempotent: a file is imported only if no scenario with the same slug exists, so admin edits are never overwritten.
 * Seed files pass the same validation as admin input; an invalid file stops the start-up (fail fast).
 */
@Component
@Order(20)
public class ScenarioSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ScenarioSeeder.class);

    private final AppProperties properties;
    private final ScenarioService scenarioService;
    private final AuthoringService authoring;
    private final ObjectMapper objectMapper;

    public ScenarioSeeder(AppProperties properties, ScenarioService scenarioService, AuthoringService authoring,
                         ObjectMapper objectMapper) {
        this.properties = properties;
        this.scenarioService = scenarioService;
        this.authoring = authoring;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        if (properties.scenarios() != null && !properties.scenarios().seedEnabled()) {
            return;
        }
        Resource[] files = new PathMatchingResourcePatternResolver().getResources("classpath:scenarios/*.json");
        Arrays.sort(files, Comparator.comparing(Resource::getFilename));
        for (Resource file : files) {
            ScenarioDefinition definition;
            try (InputStream in = file.getInputStream()) {
                definition = objectMapper.readValue(in, ScenarioDefinition.class);
            }
            if (scenarioService.existsBySlug(definition.slug())) {
                continue;
            }
            Scenario scenario = scenarioService.create(definition);
            log.info("Seeded scenario '{}' from {}", definition.slug(), file.getFilename());
            try {
                authoring.publish(null, scenario.getId(), "Initial version (seed)");
            } catch (ApiException e) {
                log.warn("Seed '{}' stays a draft: {}", definition.slug(), e.getMessage());
            }
        }
    }
}
