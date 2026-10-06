package am.cybersim.auth;

import am.cybersim.support.IntegrationTest;
import am.cybersim.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends IntegrationTest {

    @Test
    void registerCreatesStudentAndNeverReturnsPassword() throws Exception {
        String email = uniqueEmail("Reg");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new Object[]{"email", email, "displayName", "Alice", "password", PASSWORD})))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email.toLowerCase()))
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void registerIgnoresRoleInRequestBody() throws Exception {
        String email = uniqueEmail("escalate");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new Object[]{"email", email, "displayName", "Mallory",
                                "password", PASSWORD, "role", "ADMIN"})))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("STUDENT"));
    }

    @Test
    void registerWithExistingEmailReturnsConflict() throws Exception {
        String email = uniqueEmail("dup");
        createUser(email, Role.STUDENT);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new Object[]{"email", email.toUpperCase(), "displayName", "Bob", "password", PASSWORD})))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }

    @Test
    void registerValidatesInput() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new Object[]{"email", "not-an-email", "displayName", "", "password", "short"})))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("email")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("password")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("displayName")));
    }

    @Test
    void loginReturnsTokenThatAuthenticatesMe() throws Exception {
        String email = uniqueEmail("login");
        createUser(email, Role.STUDENT);

        String token = login(email, PASSWORD);

        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.lastLoginAt").exists());
    }

    @Test
    void loginWithWrongPasswordOrUnknownEmailReturnsSameError() throws Exception {
        String email = uniqueEmail("wrong");
        createUser(email, Role.STUDENT);

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new Object[]{"email", email, "password", "WrongPassword1"})))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new Object[]{"email", "nobody@test.local", "password", "WrongPassword1"})))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void disabledUserCannotLogIn() throws Exception {
        String email = uniqueEmail("disabled");
        var user = createUser(email, Role.STUDENT);
        user.setEnabled(false);
        userRepository.save(user);

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new Object[]{"email", email, "password", PASSWORD})))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointWithoutTokenReturnsJson401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void tamperedTokenIsRejected() throws Exception {
        String token = tokenFor(Role.STUDENT);
        String tampered = token.substring(0, token.length() - 4) + "AAAA";
        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(tampered)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenHasBearerType() throws Exception {
        String email = uniqueEmail("type");
        createUser(email, Role.STUDENT);
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new Object[]{"email", email, "password", PASSWORD})))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken", startsWith("ey")));
    }
}
