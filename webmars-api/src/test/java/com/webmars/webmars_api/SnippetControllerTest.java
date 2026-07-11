package com.webmars.webmars_api;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Enhancement Plan §9.1 — SnippetController scenarios. */
class SnippetControllerTest extends PostgresTestBase {

    @Test
    void owner_canRead_privateSnippet() throws Exception {
        String alice = registerAndLogin("alice_snip1", "password123");
        long id = saveSnippet(alice, "private one", "li $t0, 1", "PRIVATE");
        mvc.perform(get("/snippets/" + id).header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("li $t0, 1"))
                .andExpect(jsonPath("$.owner.username").value("alice_snip1"));
    }

    @Test
    void privateSnippet_returns404_toNonOwner() throws Exception {
        String alice = registerAndLogin("alice_snip2", "password123");
        long id = saveSnippet(alice, "alice private", "li $t0, 1", "PRIVATE");
        String bob = registerAndLogin("bob_snip2", "password123");
        mvc.perform(get("/snippets/" + id).header("Authorization", "Bearer " + bob))
                .andExpect(status().isNotFound());
    }

    @Test
    void publicSnippet_isReadable_byAnonymous() throws Exception {
        String alice = registerAndLogin("alice_snip3", "password123");
        long id = saveSnippet(alice, "public hello", "li $t0, 42", "PUBLIC");
        mvc.perform(get("/snippets/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("li $t0, 42"));
    }

    @Test
    void delete_byNonOwner_returns404_andSnippetSurvives() throws Exception {
        String alice = registerAndLogin("alice_snip4", "password123");
        long id = saveSnippet(alice, "title", "code", "PUBLIC");
        String bob = registerAndLogin("bob_snip4", "password123");
        mvc.perform(delete("/snippets/" + id).header("Authorization", "Bearer " + bob))
                .andExpect(status().isNotFound());
        assertThat(snippets.findById(id)).isPresent();
    }

    @Test
    void delete_byOwner_returns204_andRemovesTheRow() throws Exception {
        String alice = registerAndLogin("alice_snip5", "password123");
        long id = saveSnippet(alice, "bye", "nop", "PRIVATE");
        mvc.perform(delete("/snippets/" + id).header("Authorization", "Bearer " + alice))
                .andExpect(status().isNoContent());
        assertThat(snippets.findById(id)).isEmpty();
    }

    @Test
    void update_byOwner_changesFields_andPreservesOwner() throws Exception {
        String alice = registerAndLogin("alice_snip6", "password123");
        long id = saveSnippet(alice, "before", "li $t0, 1", "PRIVATE");
        mvc.perform(put("/snippets/" + id)
                        .header("Authorization", "Bearer " + alice)
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("title", "after", "code", "li $t0, 2"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("after"))
                .andExpect(jsonPath("$.code").value("li $t0, 2"))
                .andExpect(jsonPath("$.owner.username").value("alice_snip6"));
    }

    @Test
    void update_byNonOwner_returns404() throws Exception {
        String alice = registerAndLogin("alice_snip7", "password123");
        long id = saveSnippet(alice, "mine", "nop", "PUBLIC");
        String bob = registerAndLogin("bob_snip7", "password123");
        mvc.perform(put("/snippets/" + id)
                        .header("Authorization", "Bearer " + bob)
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("title", "stolen"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void visibilityToggle_viaPut_flipsPrivateToPublic() throws Exception {
        String alice = registerAndLogin("alice_snip8", "password123");
        long id = saveSnippet(alice, "toggle me", "nop", "PRIVATE");
        mvc.perform(put("/snippets/" + id)
                        .header("Authorization", "Bearer " + alice)
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("visibility", "PUBLIC"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visibility").value("PUBLIC"));
        // Now anonymously readable.
        mvc.perform(get("/snippets/" + id)).andExpect(status().isOk());
    }

    @Test
    void publicFeed_paginates_andSerializesOwners() throws Exception {
        String alice = registerAndLogin("alice_snip9", "password123");
        saveSnippet(alice, "pub one", "nop", "PUBLIC");
        saveSnippet(alice, "pub two", "nop", "PUBLIC");
        saveSnippet(alice, "priv", "nop", "PRIVATE");
        // The populated feed 500'd in production before the DTO fix —
        // this pins the regression.
        mvc.perform(get("/snippets/public?page=0&size=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].owner.username").value("alice_snip9"));
    }

    @Test
    void mine_returnsOnlyTheCallersSnippets() throws Exception {
        String alice = registerAndLogin("alice_snip10", "password123");
        saveSnippet(alice, "a1", "nop", "PRIVATE");
        String bob = registerAndLogin("bob_snip10", "password123");
        saveSnippet(bob, "b1", "nop", "PRIVATE");
        mvc.perform(get("/snippets/mine").header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("a1"));
    }
}
