package com.webmars.webmars_api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 32) @Pattern(regexp = "^[a-zA-Z0-9_]+$") String username,
        @NotBlank @Size(min = 8, max = 128) String password
) {}
