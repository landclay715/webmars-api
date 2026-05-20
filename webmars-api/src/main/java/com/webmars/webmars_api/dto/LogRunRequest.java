package com.webmars.webmars_api.dto;

import com.webmars.webmars_api.Run;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.PositiveOrZero;


public record LogRunRequest (
        @NotNull(message = "Snippet ID is required")
        Long snippetId,
        @PositiveOrZero(message = "Duration must be zero or positive")
        Integer durationMs,
        @PositiveOrZero(message = "Instructions executed must be zero or positive")
        Integer instructionsExecuted,
        @NotNull(message = "Exit status is required")
        Run.ExitStatus exitStatus,
        @Size(max = 2048, message = "Error message must be under 2048 characters")
        String errorMessage
) {}