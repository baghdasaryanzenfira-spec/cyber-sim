package am.cybersim.simulation;

import am.cybersim.scenario.ScenarioRepository;
import am.cybersim.support.IntegrationTest;
import am.cybersim.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end test of the main student flow (MVP): create → start → investigate → respond → hint → complete →
 * score + AI feedback → history, against real PostgreSQL with the seeded SSH brute-force scenario.
 */
class SimulationFlowIntegrationTest extends IntegrationTest {

    static final List<String> PERFECT_RUN = List.of("inspect-auth-log", "inspect-flow-logs", "inspect-process-list",
            "identify-bruteforce", "identify-compromised-vm", "isolate-vm", "disable-admin-account",
            "remove-backdoor-user", "rotate-credentials", "restrict-ssh");

    @Autowired
    ScenarioRepository scenarioRepository;

    String token;
    Long scenarioId;

    @BeforeEach
    void setUp() throws Exception {
        token = tokenFor(Role.STUDENT);
        scenarioId = scenarioRepository.findBySlug("ssh-bruteforce-vm").orElseThrow().getId();
    }

    @Test
    void perfectRunScoresHundredAndProducesFeedback() throws Exception {
        long id = createAndStart();

        for (String action : PERFECT_RUN) {
            perform(id, action).andExpect(status().isOk());
        }
        JsonNode result = body(mockMvc.perform(post("/api/simulations/" + id + "/complete")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.scorePercent").value(100))
                .andExpect(jsonPath("$.missedActions.length()").value(0))
                .andExpect(jsonPath("$.feedbackSource").value("MOCK"))
                .andExpect(jsonPath("$.feedback.summary").exists())
                .andExpect(jsonPath("$.recommendedSolution").exists()));
        assertThat(result.get("evidence").get("total").asInt()).isGreaterThan(5);

        // history shows the completed attempt with its score
        mockMvc.perform(get("/api/simulations").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].scorePercent").value(100));
    }

    @Test
    void stateProgressesAndInvestigationRevealsLogs() throws Exception {
        JsonNode created = body(mockMvc.perform(post("/api/simulations").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"scenarioId\":" + scenarioId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.availableActions.length()").value(0)));
        long id = created.get("id").asLong();

        // creating again resumes the same attempt
        mockMvc.perform(post("/api/simulations").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"scenarioId\":" + scenarioId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));

        JsonNode started = body(mockMvc.perform(post("/api/simulations/" + id + "/start").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING")));
        int initialEvents = started.get("events").size();
        String startedJson = started.toString();
        assertThat(startedJson).doesNotContain("\"outcome\"", "\"points\"", "EXPECTED", "HARMFUL");
        assertThat(started.get("events").get(0).get("evidence")).isNull();

        perform(id, "inspect-auth-log")
                .andExpect(jsonPath("$.revealedEvents").value(5))
                .andExpect(jsonPath("$.simulation.status").value("INVESTIGATING"))
                .andExpect(jsonPath("$.action.points").doesNotExist());

        perform(id, "identify-compromised-vm");
        perform(id, "isolate-vm")
                .andExpect(jsonPath("$.simulation.status").value("RESPONDING"))
                .andExpect(jsonPath("$.simulation.resources[0].status").value("ISOLATED"));

        JsonNode detail = body(mockMvc.perform(get("/api/simulations/" + id).header("Authorization", bearer(token))));
        // 5 revealed log lines + 3 analyst events
        assertThat(detail.get("events").size()).isEqualTo(initialEvents + 5 + 3);
    }

    @Test
    void mistakesHintsAndOrderAreScoredDeterministically() throws Exception {
        long id = createAndStart();

        perform(id, "isolate-vm");              // before identification: 20 - 5 = 15
        perform(id, "terminate-vm");            // harmful: -10
        perform(id, "inspect-app-logs");        // neutral: 0
        perform(id, "inspect-auth-log");        // +10
        perform(id, "inspect-auth-log");        // duplicate: 0
        mockMvc.perform(post("/api/simulations/" + id + "/assistant/hint").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("MOCK"))
                .andExpect(jsonPath("$.hintsUsed").value(1));   // -2

        mockMvc.perform(post("/api/simulations/" + id + "/complete").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rawScore").value(13))
                .andExpect(jsonPath("$.scorePercent").value(13))
                .andExpect(jsonPath("$.hintPenaltyTotal").value(2))
                .andExpect(jsonPath("$.breakdown[*].kind", hasItem("OUT_OF_ORDER")))
                .andExpect(jsonPath("$.breakdown[*].kind", hasItem("HARMFUL")))
                .andExpect(jsonPath("$.breakdown[*].kind", hasItem("DUPLICATE")))
                .andExpect(jsonPath("$.feedback.orderIssues.length()").value(1))
                .andExpect(jsonPath("$.performedActions[1].outcome").value("HARMFUL"));
    }

    @Test
    void invalidOperationsAreRejected() throws Exception {
        long id = create();
        perform(id, "inspect-auth-log").andExpect(status().isConflict());           // not started
        mockMvc.perform(get("/api/simulations/" + id + "/result").header("Authorization", bearer(token)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_COMPLETED"));

        mockMvc.perform(post("/api/simulations/" + id + "/start").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/simulations/" + id + "/start").header("Authorization", bearer(token)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        perform(id, "rm-rf-everything").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNKNOWN_ACTION"));
        mockMvc.perform(post("/api/simulations/" + id + "/actions").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"actionKey\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/simulations/" + id + "/complete").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        perform(id, "isolate-vm").andExpect(status().isConflict());
        mockMvc.perform(post("/api/simulations/" + id + "/assistant/hint").header("Authorization", bearer(token)))
                .andExpect(status().isConflict());
    }

    @Test
    void otherStudentsCannotSeeOrModifyMySimulation() throws Exception {
        long id = createAndStart();
        String intruder = tokenFor(Role.STUDENT);

        mockMvc.perform(get("/api/simulations/" + id).header("Authorization", bearer(intruder)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/simulations/" + id + "/actions").header("Authorization", bearer(intruder))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"actionKey\":\"terminate-vm\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/simulations/" + id + "/assistant/ask").header("Authorization", bearer(intruder))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"hi\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void evidenceFlaggingAndAssistantConversation() throws Exception {
        long id = createAndStart();
        JsonNode detail = body(mockMvc.perform(get("/api/simulations/" + id).header("Authorization", bearer(token))));
        long eventId = detail.get("events").get(1).get("id").asLong();

        mockMvc.perform(put("/api/simulations/" + id + "/events/" + eventId + "/flag").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"flagged\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[1].flagged").value(true));

        mockMvc.perform(post("/api/simulations/" + id + "/assistant/ask").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"What does isolating a VM mean?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("QUESTION"))
                .andExpect(jsonPath("$.text").isNotEmpty());
        mockMvc.perform(post("/api/simulations/" + id + "/assistant/ask").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"" + "x".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/simulations/" + id + "/assistant/messages").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].question").value("What does isolating a VM mean?"));
    }

    @Test
    void abandonedSimulationAllowsANewAttempt() throws Exception {
        long id = createAndStart();
        mockMvc.perform(post("/api/simulations/" + id + "/abandon").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ABANDONED"));
        long second = create();
        assertThat(second).isNotEqualTo(id);
    }

    private long create() throws Exception {
        return body(mockMvc.perform(post("/api/simulations").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"scenarioId\":" + scenarioId + "}"))
                .andExpect(status().is2xxSuccessful())).get("id").asLong();
    }

    private long createAndStart() throws Exception {
        long id = create();
        mockMvc.perform(post("/api/simulations/" + id + "/start").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        return id;
    }

    private org.springframework.test.web.servlet.ResultActions perform(long id, String actionKey) throws Exception {
        return mockMvc.perform(post("/api/simulations/" + id + "/actions").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"actionKey\":\"" + actionKey + "\"}"));
    }
}
