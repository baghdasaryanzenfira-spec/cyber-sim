package am.cybersim.ai;

import am.cybersim.ai.AiProvider.AiRequest;
import am.cybersim.scenario.dto.ScenarioDefinition;
import am.cybersim.support.TestDefinitions;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** The offline provider must be deterministic and must never change scoring-relevant structure. */
class MockAiProviderTest {

    ObjectMapper objectMapper = JsonMapper.builder().build();
    MockAiProvider provider = new MockAiProvider(objectMapper);
    ScenarioDefinition draft = TestDefinitions.small();

    @Test
    void generationReturnsTheDraftUnchanged() {
        var response = provider.complete(new AiRequest(new AiPayload.Generation(draft, "brief"), "s", "u", null));
        ScenarioDefinition parsed = objectMapper.readValue(response.text(), ScenarioDefinition.class);
        assertThat(parsed).isEqualTo(draft);
    }

    @Test
    void variationKeepsTheStructureAndSetsTheNewSlug() {
        var response = provider.complete(
                new AiRequest(new AiPayload.Variation(draft, "small-scenario-var-1"), "s", "u", null));
        ScenarioDefinition variant = objectMapper.readValue(response.text(), ScenarioDefinition.class);
        assertThat(variant.slug()).isEqualTo("small-scenario-var-1");
        ScenarioVariationService.requireSameStructure(draft, variant); // throws when the structure changed
    }

    @Test
    void translationEchoesTheSource() {
        var response = provider.complete(
                new AiRequest(new AiPayload.Translation("Isolate the VM", "Armenian"), "s", "u", null));
        assertThat(response.text()).isEqualTo("Isolate the VM");
    }
}
