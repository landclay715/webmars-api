package com.webmars.webmars_api;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Enhancement Plan §9.1 — AuthController scenarios. */
class AuthControllerTest extends PostgresTestBase {

    @Test
    void register_succeeds_withoutLeakingPassword() throws Exception {
        mvc.perform(post("/auth/register")
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("username", "alice_auth1", "password", "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice_auth1"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void register_duplicateUsername_returns409() throws Exception {
        register("alice_auth2", "password123");
        mvc.perform(post("/auth/register")
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("username", "alice_auth2", "password", "password456"))))
                .andExpect(status().isConflict());
    }

    @Test
    void register_invalidUsername_returns400() throws Exception {
        // Too short AND illegal chars per the DTO's @Size/@Pattern.
        mvc.perform(post("/auth/register")
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("username", "a!", "password", "password123"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_succeeds_andReturnsAToken() throws Exception {
        register("alice_auth3", "password123");
        String token = login("alice_auth3", "password123");
        org.assertj.core.api.Assertions.assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    void login_wrongPassword_returns401() throws Exception {
        register("alice_auth4", "password123");
        mvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("username", "alice_auth4", "password", "wrongpassword"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_unknownUser_returns401_withSameMessageAsWrongPassword() throws Exception {
        // Same 401 + same body for both cases so usernames can't be
        // enumerated (Enhancement Plan §2.4).
        mvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("username", "nobody_here", "password", "password123"))))
                .andExpect(status().isUnauthorized());
    }
}
