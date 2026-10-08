package am.cybersim.authoring;

import am.cybersim.scenario.ScenarioEnums.ActionOutcome;
import am.cybersim.scenario.dto.ScenarioDefinition;
import am.cybersim.support.IntegrationTest;
import am.cybersim.support.TestDefinitions;
import am.cybersim.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The full admin authoring workflow over HTTP against a real PostgreSQL: generate -> evaluate -> publish -> version. */
class AuthoringIntegrationTest extends IntegrationTest {

    String admin;

    @BeforeEach
    void setUp() throws Exception {
        admin = tokenFor(Role.ADMIN);
    }

    String auth() {
        return bearer(admin);
    }

    JsonNode generate(String type, String title, boolean useAi) throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of("type", type, "title", title, "useAi", useAi));
        return body(mockMvc.perform(post("/api/admin/scenarios/generate").header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()));
    }

    String unique(String prefix) {
        return prefix + " " + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void anonymousCallersCannotUseTheAuthoringApi() throws Exception {
        mockMvc.perform(get("/api/admin/scenarios")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/scenarios/generate").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void seedScenariosAreImportedAndPublishedAsVersionOne() throws Exception {
        mockMvc.perform(get("/api/admin/scenarios").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.slug=='ssh-bruteforce-vm')].status").value(hasItem("PUBLISHED")))
                .andExpect(jsonPath("$[?(@.slug=='ssh-bruteforce-vm')].publishedVersion").value(hasItem(1)))
                .andExpect(jsonPath("$[?(@.slug=='compromised-credentials')].status").value(hasItem("PUBLISHED")))
                .andExpect(jsonPath("$[?(@.slug=='public-storage-bucket')].status").value(hasItem("PUBLISHED")));
    }

    @Test
    void generateEvaluatePublishAndVersionASshBruteForceScenario() throws Exception {
        String title = unique("Customer SSH brute force");
        JsonNode created = generate("SSH_BRUTE_FORCE", title, false);
        long id = created.get("scenario").get("id").asLong();
        assertThat(created.get("scenario").get("status").asString()).isEqualTo("DRAFT");
        assertThat(created.get("scenario").get("definition").get("title").asString()).isEqualTo(title);
        assertThat(created.get("scenario").get("definition").get("slug").asString()).startsWith("customer-ssh-brute-force");

        // graph
        mockMvc.perform(get("/api/admin/scenarios/" + id + "/graph").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes[?(@.type=='ACTION')]").isNotEmpty())
                .andExpect(jsonPath("$.edges[?(@.type=='PREREQUISITE')]").isNotEmpty())
                .andExpect(jsonPath("$.edges[?(@.type=='REVEALS')]").isNotEmpty());

        // validation
        mockMvc.perform(post("/api/admin/scenarios/" + id + "/validate").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.errorCount").value(0));

        // test runner: both paths
        mockMvc.perform(post("/api/admin/scenarios/" + id + "/test-run").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(true))
                .andExpect(jsonPath("$.paths", hasSize(2)))
                .andExpect(jsonPath("$.paths[0].path").value("CORRECT"))
                .andExpect(jsonPath("$.paths[0].scorePercent").value(100))
                .andExpect(jsonPath("$.paths[1].path").value("DANGEROUS"))
                .andExpect(jsonPath("$.paths[1].scorePercent").value(0));

        // evaluate = everything + publishing gate
        JsonNode evaluation = body(mockMvc.perform(post("/api/admin/scenarios/" + id + "/evaluate")
                        .header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publishable").value(true))
                .andExpect(jsonPath("$.blockers", hasSize(0))));
        int quality = evaluation.get("quality").get("score").asInt();
        assertThat(quality).isGreaterThanOrEqualTo(QualityScorer.PUBLISH_THRESHOLD);

        // publish -> version 1
        mockMvc.perform(post("/api/admin/scenarios/" + id + "/publish").header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"changeNote\":\"first release\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.publishedVersion").value(1));

        // editing turns it back into a draft but keeps version 1; publishing again creates version 2
        ScenarioDefinition def = objectMapper.treeToValue(
                created.get("scenario").get("definition"), ScenarioDefinition.class);
        ScenarioDefinition edited = new ScenarioDefinition(def.slug(), def.title() + " (edited)", def.summary(),
                def.description(), def.difficulty(), def.category(), def.estimatedMinutes(),
                def.incidentExplanation(), def.recommendedSolution(), def.hintPenalty(), def.outOfOrderPenalty(),
                def.learningObjectives(), def.resources(), def.events(), def.actions(), def.hints());
        mockMvc.perform(put("/api/admin/scenarios/" + id).header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(edited)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.revision").value(2))
                .andExpect(jsonPath("$.publishedVersion").value(1));
        mockMvc.perform(post("/api/admin/scenarios/" + id + "/publish").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publishedVersion").value(2));

        mockMvc.perform(get("/api/admin/scenarios/" + id + "/versions").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].versionNumber").value(2))
                .andExpect(jsonPath("$[1].versionNumber").value(1))
                .andExpect(jsonPath("$[1].changeNote").value("first release"));

        // versions are immutable snapshots: version 1 still has the original title
        mockMvc.perform(get("/api/admin/scenarios/" + id + "/versions/1").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.definition.title").value(title))
                .andExpect(jsonPath("$.quality.components", hasSize(5)));
        mockMvc.perform(get("/api/admin/scenarios/" + id + "/versions/2").header("Authorization", auth()))
                .andExpect(jsonPath("$.definition.title").value(title + " (edited)"));
        mockMvc.perform(get("/api/admin/scenarios/" + id + "/versions/99").header("Authorization", auth()))
                .andExpect(status().isNotFound());

        // a published scenario cannot be deleted, only archived; archived ones cannot be edited
        mockMvc.perform(delete("/api/admin/scenarios/" + id).header("Authorization", auth()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SCENARIO_PUBLISHED"));
        mockMvc.perform(patch("/api/admin/scenarios/" + id + "/archive").header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"archived\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ARCHIVED"));
        mockMvc.perform(put("/api/admin/scenarios/" + id).header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(edited)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SCENARIO_ARCHIVED"));
    }

    @Test
    void everyScenarioTypeGeneratesAPublishableDraftWithParameters() throws Exception {
        for (String type : new String[]{"SSH_BRUTE_FORCE", "COMPROMISED_CREDENTIALS", "PUBLIC_STORAGE_BUCKET"}) {
            String body = objectMapper.writeValueAsString(java.util.Map.of("type", type, "title", unique(type),
                    "attackerIp", "198.51.100.77", "difficulty", "ADVANCED"));
            JsonNode created = body(mockMvc.perform(post("/api/admin/scenarios/generate").header("Authorization", auth())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated()));
            long id = created.get("scenario").get("id").asLong();
            assertThat(created.get("scenario").get("definition").get("difficulty").asString()).isEqualTo("ADVANCED");
            assertThat(created.get("scenario").get("definition").toString()).contains("198.51.100.77");
            mockMvc.perform(post("/api/admin/scenarios/" + id + "/evaluate").header("Authorization", auth()))
                    .andExpect(jsonPath("$.publishable").value(true));
            mockMvc.perform(delete("/api/admin/scenarios/" + id).header("Authorization", auth()))
                    .andExpect(status().isNoContent());
        }
    }

    @Test
    void generationWithAiUsesTheValidatedPipeline() throws Exception {
        JsonNode created = generate("PUBLIC_STORAGE_BUCKET", unique("AI bucket"), true);
        assertThat(created.get("aiSource").asString()).isIn("AI", "MOCK", "FALLBACK");
        assertThat(created.get("scenario").get("status").asString()).isEqualTo("DRAFT");
    }

    @Test
    void generationRejectsInvalidParameters() throws Exception {
        mockMvc.perform(post("/api/admin/scenarios/generate").header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"SSH_BRUTE_FORCE\",\"attackerIp\":\"not-an-ip\",\"primaryAsset\":\"bad name!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("attackerIp")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("primaryAsset")));
        mockMvc.perform(post("/api/admin/scenarios/generate").header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void publishingIsBlockedWhileValidationHasErrors() throws Exception {
        // structurally valid, but there is no harmful action -> the dangerous path cannot be tested
        ScenarioDefinition noTrap = TestDefinitions.mapActions(
                TestDefinitions.withSlug(TestDefinitions.small(), "no-trap-" + UUID.randomUUID().toString().substring(0, 8)),
                actions -> actions.stream().filter(a -> a.outcome() != ActionOutcome.HARMFUL).toList());
        JsonNode created = body(mockMvc.perform(post("/api/admin/scenarios").header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(noTrap)))
                .andExpect(status().isCreated()));
        long id = created.get("id").asLong();

        mockMvc.perform(post("/api/admin/scenarios/" + id + "/validate").header("Authorization", auth()))
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.issues[*].code", hasItem("NO_DANGEROUS_PATH")));
        mockMvc.perform(post("/api/admin/scenarios/" + id + "/test-run").header("Authorization", auth()))
                .andExpect(status().isOk()).andExpect(content().string(""));
        mockMvc.perform(post("/api/admin/scenarios/" + id + "/publish").header("Authorization", auth()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PUBLISH_BLOCKED"));
        mockMvc.perform(get("/api/admin/scenarios/" + id + "/versions").header("Authorization", auth()))
                .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(delete("/api/admin/scenarios/" + id).header("Authorization", auth()))
                .andExpect(status().isNoContent());
    }

    @Test
    void liveEvaluationOfAnUnsavedDefinitionNeedsNoStorage() throws Exception {
        mockMvc.perform(post("/api/admin/scenarios/evaluate").header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TestDefinitions.small())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validation.valid").value(true))
                .andExpect(jsonPath("$.tests.paths", hasSize(2)))
                .andExpect(jsonPath("$.quality.score").isNumber());
    }

    @Test
    void invalidDefinitionIsRejectedWithAllErrors() throws Exception {
        String json = objectMapper.writeValueAsString(TestDefinitions.withSlug(TestDefinitions.small(), "Bad Slug"))
                .replace("\"points\":30", "\"points\":-30");
        mockMvc.perform(post("/api/admin/scenarios").header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_SCENARIO"))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("slug")));
    }
}
