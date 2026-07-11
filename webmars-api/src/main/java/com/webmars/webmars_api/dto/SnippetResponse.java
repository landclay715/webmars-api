package com.webmars.webmars_api.dto;

import com.webmars.webmars_api.Snippet;
import java.time.LocalDateTime;

/**
 * Response DTO for snippet endpoints (Enhancement Plan §7.5).
 *
 * Mapping to a record inside the controller (while the owner is still
 * fetched) fixes the production 500s: serializing the lazy {@code owner}
 * proxy after the session closed (spring.jpa.open-in-view=false) threw
 * LazyInitializationException on every GET that touched an owned snippet.
 *
 * The JSON shape intentionally mirrors what the entity used to serialize
 * ({@code owner: { username }}) so existing frontend clients keep working.
 */
public record SnippetResponse(
        Long id,
        String title,
        String code,
        OwnerResponse owner,
        Snippet.Visibility visibility,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public record OwnerResponse(String username) {}

    public static SnippetResponse from(Snippet s) {
        return new SnippetResponse(
                s.getId(),
                s.getTitle(),
                s.getCode(),
                s.getOwner() == null ? null : new OwnerResponse(s.getOwner().getUsername()),
                s.getVisibility(),
                s.getCreatedAt(),
                s.getUpdatedAt()
        );
    }
}
