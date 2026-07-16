package com.webmars.webmars_api.dto;

import com.webmars.webmars_api.Snippet;
import jakarta.validation.constraints.Size;

/**
 * Partial update for PUT /snippets/{id} (Enhancement Plan §7.5).
 * Every field is optional; null means "leave unchanged".
 */
public record UpdateSnippetRequest(
        @Size(max = 255, message = "Title must be under 255 characters")
        String title,

        @Size(max = 65536, message = "Code must be under 65536 characters")
        String code,

        Snippet.Visibility visibility
) {}
