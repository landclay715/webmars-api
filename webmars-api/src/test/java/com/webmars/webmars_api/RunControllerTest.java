package com.webmars.webmars_api;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Enhancement Plan §9.1 — RunController scenarios. */
class RunControllerTest extends PostgresTestBase {

    private void logRun(String token, long snippetId) throws Exception {
        mvc.perform(post("/runs")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of(
                                "snippetId", snippetId,
                                "durationMs", 42,
                                "instructionsExecuted", 7,
                                "exitStatus", "COMPLETED"))))
                .andExpect(status().isOk());
    }

    @Test
    void logRun_setsUserFromJwt_notFromBody() throws Exception {
        String alice = registerAndLogin("alice_run1", "password123");
        long id = saveSnippet(alice, "runnable", "nop", "PUBLIC");
        logRun(alice, id);
        Run saved = runs.findAll().get(0);
        assertThat(saved.getUser().getUsername()).isEqualTo("alice_run1");
    }

    @Test
    void logRun_onOwnPrivateSnippet_succeeds() throws Exception {
        // Pins the production regression: the visibility check touched
        // the lazy owner proxy and 500'd for every private snippet.
        String alice = registerAndLogin("alice_run2", "password123");
        long id = saveSnippet(alice, "private run", "nop", "PRIVATE");
        logRun(alice, id);
    }

    @Test
    void logRun_onSomeoneElsesPrivateSnippet_returns404() throws Exception {
        String alice = registerAndLogin("alice_run3", "password123");
        long id = saveSnippet(alice, "secret", "nop", "PRIVATE");
        String bob = registerAndLogin("bob_run3", "password123");
        mvc.perform(post("/runs")
                        .header("Authorization", "Bearer " + bob)
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("snippetId", id, "exitStatus", "COMPLETED"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void logRun_missingSnippet_returns404() throws Exception {
        String alice = registerAndLogin("alice_run4", "password123");
        mvc.perform(post("/runs")
                        .header("Authorization", "Bearer " + alice)
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("snippetId", 999999, "exitStatus", "COMPLETED"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void recentAndMostRun_areScopedToTheAuthenticatedUser() throws Exception {
        String alice = registerAndLogin("alice_run5", "password123");
        long aliceSnip = saveSnippet(alice, "alice prog", "nop", "PUBLIC");
        logRun(alice, aliceSnip);
        logRun(alice, aliceSnip);

        String bob = registerAndLogin("bob_run5", "password123");
        long bobSnip = saveSnippet(bob, "bob prog", "nop", "PUBLIC");
        logRun(bob, bobSnip);

        // Pins the production regression: /runs/recent 500'd once rows
        // existed (lazy snippet proxy in RunResponse.from).
        mvc.perform(get("/runs/recent").header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].snippetTitle").value("alice prog"));

        mvc.perform(get("/runs/most-run").header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].runCount").value(2));

        mvc.perform(get("/runs/recent").header("Authorization", "Bearer " + bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].snippetTitle").value("bob prog"));
    }
}
