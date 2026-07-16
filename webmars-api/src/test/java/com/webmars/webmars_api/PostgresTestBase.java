package com.webmars.webmars_api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared base for integration tests (Enhancement Plan §8.4): spins up
 * one ephemeral PostgreSQL container for the whole test run, points
 * Spring + Flyway at it, and cleans the tables between tests. Rate
 * limits are raised so MockMvc's single mock IP never trips 429s.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@AutoConfigureMockMvc
public abstract class PostgresTestBase {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("webmars_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.flyway.url", postgres::getJdbcUrl);
        r.add("spring.flyway.user", postgres::getUsername);
        r.add("spring.flyway.password", postgres::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        r.add("jwt.secret", () -> "test-secret-must-be-at-least-32-bytes-long-for-hs256");
        r.add("ratelimit.login.limit", () -> "10000");
        r.add("ratelimit.register.limit", () -> "10000");
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected UserRepository users;
    @Autowired protected SnippetRepository snippets;
    @Autowired protected RunRepository runs;

    @AfterEach
    void cleanDb() {
        runs.deleteAll();
        snippets.deleteAll();
        users.deleteAll();
    }

    // ── shared helpers ─────────────────────────────────────────────

    protected void register(String username, String password) throws Exception {
        mvc.perform(post("/auth/register")
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("username", username, "password", password))))
                .andExpect(status().isOk());
    }

    protected String login(String username, String password) throws Exception {
        MvcResult result = mvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        @SuppressWarnings("unchecked")
        Map<String, String> body = json.readValue(result.getResponse().getContentAsString(), Map.class);
        return body.get("token");
    }

    protected String registerAndLogin(String username, String password) throws Exception {
        register(username, password);
        return login(username, password);
    }

    protected long saveSnippet(String token, String title, String code, String visibility) throws Exception {
        MvcResult result = mvc.perform(post("/snippets/save")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("title", title, "code", code, "visibility", visibility))))
                .andExpect(status().isOk())
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }
}
