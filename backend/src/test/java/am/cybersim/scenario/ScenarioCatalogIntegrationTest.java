package am.cybersim.scenario;

import am.cybersim.support.IntegrationTest;
import am.cybersim.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ScenarioCatalogIntegrationTest extends IntegrationTest {

    @Autowired
    ScenarioRepository scenarioRepository;

    @Test
    @Transactional
    void seedScenariosAreImportedWithAllChildrenAndJsonProperties() {
        Scenario ssh = scenarioRepository.findBySlug("ssh-bruteforce-vm").orElseThrow();
        assertThat(ssh.maxScore()).isEqualTo(100);
        assertThat(ssh.getActions()).hasSizeGreaterThan(5);
        assertThat(ssh.getResources().getFirst().getProperties()).containsEntry("publicIp", "198.51.100.24");
        assertThat(scenarioRepository.findBySlug("compromised-credentials").orElseThrow().maxScore()).isEqualTo(100);
        assertThat(scenarioRepository.findBySlug("public-storage-bucket").orElseThrow().maxScore()).isEqualTo(100);
    }

    @Test
    void studentSeesActiveScenarios() throws Exception {
        String token = tokenFor(Role.STUDENT);
        mockMvc.perform(get("/api/scenarios").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].slug", hasItems("ssh-bruteforce-vm", "compromised-credentials",
                        "public-storage-bucket")));
    }

    @Test
    void briefingContainsObjectivesButNoSolution() throws Exception {
        String token = tokenFor(Role.STUDENT);
        Long id = scenarioRepository.findBySlug("ssh-bruteforce-vm").orElseThrow().getId();
        String body = mockMvc.perform(get("/api/scenarios/" + id).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.learningObjectives.length()").value(5))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("recommendedSolution", "incidentExplanation", "outcome", "points",
                "EXPECTED", "HARMFUL");
    }

    @Test
    void catalogueRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/scenarios")).andExpect(status().isUnauthorized());
    }

    @Test
    void unknownScenarioReturns404() throws Exception {
        String token = tokenFor(Role.STUDENT);
        mockMvc.perform(get("/api/scenarios/999999").header("Authorization", bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
