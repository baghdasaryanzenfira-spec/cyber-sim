package am.cybersim.support;

import am.cybersim.user.Role;
import am.cybersim.user.User;
import am.cybersim.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class for full-stack integration tests: real Spring context, real PostgreSQL (Testcontainers),
 * HTTP layer through MockMvc. All subclasses share one cached context and one database container.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    protected static final String PASSWORD = "Str0ngPassw0rd!";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    protected String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@test.local";
    }

    protected User createUser(String email, Role role) {
        return userRepository.save(new User(email, "Test " + role, passwordEncoder.encode(PASSWORD), role, Instant.now()));
    }

    /** Creates a user and returns a valid bearer token for it. */
    protected String tokenFor(Role role) throws Exception {
        String email = uniqueEmail(role.name().toLowerCase());
        createUser(email, role);
        return login(email, PASSWORD);
    }

    protected String login(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new Object[]{"email", email, "password", password})))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asString();
    }

    protected String bearer(String token) {
        return "Bearer " + token;
    }

    /** Small helper: json("k1", v1, "k2", v2) as alternating key/value pairs. */
    protected String json(Object[] keyValues) {
        var node = objectMapper.createObjectNode();
        for (int i = 0; i < keyValues.length; i += 2) {
            node.putPOJO((String) keyValues[i], keyValues[i + 1]);
        }
        return node.toString();
    }

    protected JsonNode body(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
