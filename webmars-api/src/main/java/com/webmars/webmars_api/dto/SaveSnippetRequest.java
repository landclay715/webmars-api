package com.webmars.webmars_api.dto;

import com.webmars.webmars_api.Snippet;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveSnippetRequest(
    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must be under 255 characters")
    String title,

    @NotBlank(message = "Code is required")
    @Size(max = 65536, message = "Code must be under 65536 characters")
    String code,

    Snippet.Visibility visibility
) {}
