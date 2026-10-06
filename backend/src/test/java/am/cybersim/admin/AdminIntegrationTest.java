package am.cybersim.admin;

import am.cybersim.scenario.ScenarioRepository;
import am.cybersim.scenario.dto.ScenarioDefinition;
import am.cybersim.support.IntegrationTest;
import am.cybersim.support.TestScenarios;
import am.cybersim.user.Role;
import am.cybersim.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Admin flow (MVP): admin login → scenarios → create/edit/activate → variation → attempts → analytics. */
class AdminIntegrationTest extends IntegrationTest {

    @Autowired
    ScenarioRepository scenarioRepository;

    String admin;
    String student;

    @BeforeEach
    void setUp() throws Exception {
        admin = tokenFor(Role.ADMIN);
        student = tokenFor(Role.STUDENT);
    }

    @Test
    void studentsCannotAccessAdminApi() throws Exception {
        for (String path : new String[]{"/api/admin/users", "/api/admin/scenarios", "/api/admin/simulations",
                "/api/admin/analytics/overview", "/api/admin/analytics/mistakes"}) {
            mockMvc.perform(get(path).header("Authorization", bearer(student)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
        mockMvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void createEditDeactivateScenario() throws Exception {
        String slug = "admin-test-" + UUID.randomUUID().toString().substring(0, 8);
        ScenarioDefinition def = withSlug(TestScenarios.definition(), slug, true);

        JsonNode created = body(mockMvc.perform(post("/api/admin/scenarios").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(def)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.maxScore").value(60))
                .andExpect(jsonPath("$.definition.actions[2].outcome").value("EXPECTED")));
        long id = created.get("id").asLong();

        // visible in the student catalogue
        mockMvc.perform(get("/api/scenarios").header("Authorization", bearer(student)))
                .andExpect(jsonPath("$[*].slug", hasItem(slug)));

        // edit: version is incremented, children replaced
        ScenarioDefinition edited = new ScenarioDefinition(slug, "Edited title", def.summary(), def.description(),
                def.difficulty(), def.category(), 25, def.incidentExplanation(), def.recommendedSolution(), 3, 5, true,
                def.learningObjectives(), def.resources(), def.events(), def.actions(), def.hints());
        mockMvc.perform(put("/api/admin/scenarios/" + id).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(edited)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.definition.title").value("Edited title"))
                .andExpect(jsonPath("$.definition.hintPenalty").value(3));

        // deactivate: disappears for students
        mockMvc.perform(patch("/api/admin/scenarios/" + id + "/status").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        mockMvc.perform(get("/api/scenarios").header("Authorization", bearer(student)))
                .andExpect(jsonPath("$[*].slug", not(hasItem(slug))));
        mockMvc.perform(get("/api/scenarios/" + id).header("Authorization", bearer(student)))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidScenarioIsRejectedWithAllErrors() throws Exception {
        String json = objectMapper.writeValueAsString(withSlug(TestScenarios.definition(), "Bad Slug", true))
                .replace("\"points\":30", "\"points\":-30");
        mockMvc.perform(post("/api/admin/scenarios").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_SCENARIO"))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("slug")));
    }

    @Test
    void aiVariationIsValidatedAndStoredAsInactiveDraft() throws Exception {
        Long sshId = scenarioRepository.findBySlug("ssh-bruteforce-vm").orElseThrow().getId();
        JsonNode variation = body(mockMvc.perform(post("/api/admin/scenarios/" + sshId + "/variations")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.source").value("MOCK"))
                .andExpect(jsonPath("$.scenario.active").value(false))
                .andExpect(jsonPath("$.scenario.sourceScenarioId").value(sshId))
                .andExpect(jsonPath("$.scenario.maxScore").value(100)));
        String slug = variation.get("scenario").get("definition").get("slug").asString();
        assertThat(slug).startsWith("ssh-bruteforce-vm-var-");
        assertThat(variation.toString()).doesNotContain("203.0.113.45").contains("192.0.2.45");
    }

    @Test
    void attemptsAnalyticsAndUsersReflectStudentActivity() throws Exception {
        Long sshId = scenarioRepository.findBySlug("ssh-bruteforce-vm").orElseThrow().getId();
        long simId = body(mockMvc.perform(post("/api/simulations").header("Authorization", bearer(student))
                .contentType(MediaType.APPLICATION_JSON).content("{\"scenarioId\":" + sshId + "}"))).get("id").asLong();
        mockMvc.perform(post("/api/simulations/" + simId + "/start").header("Authorization", bearer(student)));
        for (String action : new String[]{"inspect-auth-log", "terminate-vm"}) {
            mockMvc.perform(post("/api/simulations/" + simId + "/actions").header("Authorization", bearer(student))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"actionKey\":\"" + action + "\"}"));
        }
        mockMvc.perform(post("/api/simulations/" + simId + "/assistant/hint").header("Authorization", bearer(student)));
        mockMvc.perform(post("/api/simulations/" + simId + "/complete").header("Authorization", bearer(student)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/simulations?scenarioId=" + sshId + "&status=COMPLETED")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", hasItem((int) simId)));

        mockMvc.perform(get("/api/admin/simulations/" + simId).header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.simulation.performedActions[1].outcome").value("HARMFUL"))
                .andExpect(jsonPath("$.result.scorePercent").exists())
                .andExpect(jsonPath("$.aiInteractions[*].type", hasItem("HINT")))
                .andExpect(jsonPath("$.aiInteractions[*].type", hasItem("FEEDBACK")));

        mockMvc.perform(get("/api/admin/analytics/overview").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.completed").isNumber())
                .andExpect(jsonPath("$.scoreDistribution.length()").value(5))
                .andExpect(jsonPath("$.attemptsPerDay.length()").value(14))
                .andExpect(jsonPath("$.aiUsage[*].type", hasItem("FEEDBACK")));

        mockMvc.perform(get("/api/admin/analytics/mistakes").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.harmfulActions[*].actionKey", hasItem("terminate-vm")))
                .andExpect(jsonPath("$.missedActions[*].actionKey", hasItem("isolate-vm")));

        mockMvc.perform(get("/api/admin/users").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].completed", hasItem(1)));

        // student progress + recommendations
        mockMvc.perform(get("/api/progress/me").header("Authorization", bearer(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.completed").value(1))
                .andExpect(jsonPath("$.scoreHistory.length()").value(1))
                .andExpect(jsonPath("$.scenarios.length()").isNumber());
        mockMvc.perform(get("/api/progress/me/recommendations").header("Authorization", bearer(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("MOCK"))
                .andExpect(jsonPath("$.recommendations[0].topic").isNotEmpty());
    }

    @Test
    void adminCanDisableUsersButNotThemselves() throws Exception {
        String email = uniqueEmail("victim");
        User victim = createUser(email, Role.STUDENT);
        mockMvc.perform(patch("/api/admin/users/" + victim.getId() + "/status").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new Object[]{"email", email, "password", PASSWORD})))
                .andExpect(status().isUnauthorized());

        Long adminId = body(mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(admin)))).get("id").asLong();
        mockMvc.perform(patch("/api/admin/users/" + adminId + "/status").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CANNOT_DISABLE_SELF"));
    }

    private static ScenarioDefinition withSlug(ScenarioDefinition d, String slug, boolean active) {
        return new ScenarioDefinition(slug, d.title(), d.summary(), d.description(), d.difficulty(), d.category(),
                d.estimatedMinutes(), d.incidentExplanation(), d.recommendedSolution(), d.hintPenalty(),
                d.outOfOrderPenalty(), active, d.learningObjectives(), d.resources(), d.events(), d.actions(), d.hints());
    }
}
