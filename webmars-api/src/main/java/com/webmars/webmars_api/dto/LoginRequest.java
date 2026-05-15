package com.webmars.webmars_api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Size(min = 3, max = 32) String username,
        @NotBlank @Size(min = 8, max = 128) String password
) {}
