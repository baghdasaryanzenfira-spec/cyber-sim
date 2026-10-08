package am.cybersim.integration;

import am.cybersim.support.IntegrationTest;
import am.cybersim.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The learner-module integration over HTTP (ADR-13): API-key authentication, published catalogue, attempt
 * submission with server-side verification, and the admin AI review flowing back to the learner API.
 */
class LearnerIntegrationTest extends IntegrationTest {

    static final String KEY_HEADER = "X-API-Key";
    static final String KEY = "test-learner-key";
    static final String SLUG = "ssh-bruteforce-vm";

    // ------------------------------------------------------------------ authentication boundary

    @Test
    void learnerApiRequiresTheServiceKey() throws Exception {
        mockMvc.perform(get("/api/learner/scenarios"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_API_KEY"));
        mockMvc.perform(get("/api/learner/scenarios").header(KEY_HEADER, "wrong-key"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminJwtDoesNotOpenTheLearnerApiAndTheKeyDoesNotOpenAdmin() throws Exception {
        String admin = tokenFor(Role.ADMIN);
        mockMvc.perform(get("/api/learner/scenarios").header("Authorization", bearer(admin)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/scenarios").header(KEY_HEADER, KEY))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ published content

    @Test
    void publishedCatalogueAndDefinitionAreReadable() throws Exception {
        mockMvc.perform(get("/api/learner/scenarios").header(KEY_HEADER, KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.slug=='" + SLUG + "')].publishedVersion").exists());
        mockMvc.perform(get("/api/learner/scenarios/" + SLUG).header(KEY_HEADER, KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.definition.actions").isArray());
    }

    // ------------------------------------------------------------------ submission + verification

    JsonNode definition() throws Exception {
        return body(mockMvc.perform(get("/api/learner/scenarios/" + SLUG).header(KEY_HEADER, KEY))
                .andExpect(status().isOk())).get("definition");
    }

    List<Map<String, String>> actionsWhere(JsonNode definition, String outcome, Integer limit) {
        List<Map<String, String>> list = new ArrayList<>();
        for (JsonNode a : definition.get("actions")) {
            if (outcome == null || outcome.equals(a.get("outcome").asString())) {
                list.add(Map.of("actionKey", a.get("key").asString()));
            }
            if (limit != null && list.size() >= limit) {
                break;
            }
        }
        return list;
    }

    Map<String, Object> request(String externalId, List<Map<String, String>> actions, int hints, Integer claimed) {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("externalId", externalId);
        map.put("scenarioSlug", SLUG);
        map.put("studentRef", "student-42");
        map.put("studentName", "Integration Student");
        map.put("completedAt", Instant.now().toString());
        map.put("hintsUsed", hints);
        map.put("actions", actions);
        if (claimed != null) {
            map.put("claimedScorePercent", claimed);
        }
        return map;
    }

    JsonNode submit(Map<String, Object> request, int expectedStatus) throws Exception {
        var result = mockMvc.perform(post("/api/learner/attempts").header(KEY_HEADER, KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is(expectedStatus));
        return body(result);
    }

    @Test
    void submissionIsVerifiedServerSideAndAFalseClaimIsFlagged() throws Exception {
        JsonNode def = definition();
        String id = "it-" + UUID.randomUUID();
        // harmful-only run claiming a high score: our replay must contradict the claim
        JsonNode receipt = submit(request(id, actionsWhere(def, "HARMFUL", null), 2, 90), 201);
        assertThat(receipt.get("verifiedScorePercent").asInt()).isLessThan(50);
        assertThat(receipt.get("scoreMatches").asBoolean()).isFalse();
        assertThat(receipt.get("verification").get("missedActions").size()).isGreaterThan(0);
        assertThat(receipt.get("verification").get("hintPenaltyTotal").asInt()).isGreaterThan(0);

        // the learner module can read the stored result back
        mockMvc.perform(get("/api/learner/attempts/" + id).header(KEY_HEADER, KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verifiedScorePercent").value(receipt.get("verifiedScorePercent").asInt()));
    }

    @Test
    void duplicateExternalIdIsRejectedNotDoubleStored() throws Exception {
        JsonNode def = definition();
        String id = "it-" + UUID.randomUUID();
        submit(request(id, actionsWhere(def, "EXPECTED", 3), 0, null), 201);
        JsonNode conflict = submit(request(id, actionsWhere(def, "EXPECTED", 3), 0, null), 409);
        assertThat(conflict.get("code").asString()).isEqualTo("ATTEMPT_EXISTS");
    }

    @Test
    void unknownActionKeysAreAClientError() throws Exception {
        String id = "it-" + UUID.randomUUID();
        JsonNode error = submit(request(id, List.of(Map.of("actionKey", "no-such-action")), 0, null), 422);
        assertThat(error.get("code").asString()).isEqualTo("UNKNOWN_ACTION");
    }

    // ------------------------------------------------------------------ admin review round trip

    @Test
    void adminReviewsAnAttemptAndTheLearnerModuleReceivesTheFeedback() throws Exception {
        JsonNode def = definition();
        String id = "it-" + UUID.randomUUID();
        submit(request(id, actionsWhere(def, "EXPECTED", 4), 1, null), 201);

        String admin = tokenFor(Role.ADMIN);
        JsonNode rows = body(mockMvc.perform(get("/api/admin/exams").header("Authorization", bearer(admin)))
                .andExpect(status().isOk()));
        long attemptId = -1;
        for (JsonNode row : rows.get("items")) {
            if (id.equals(row.get("externalId").asString())) {
                attemptId = row.get("id").asLong();
            }
        }
        assertThat(attemptId).isPositive();

        JsonNode reviewed = body(mockMvc.perform(post("/api/admin/exams/" + attemptId + "/review")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk()));
        JsonNode review = reviewed.get("review");
        assertThat(review.get("rating").asInt()).isBetween(0, 100);
        assertThat(review.get("message").asString()).isNotBlank();
        assertThat(review.get("source").asString()).isIn("AI", "MOCK", "FALLBACK");

        // ... and the learner module sees it on its own endpoint
        mockMvc.perform(get("/api/learner/attempts/" + id).header(KEY_HEADER, KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.review.rating").value(review.get("rating").asInt()));
    }
}
