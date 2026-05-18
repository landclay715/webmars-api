package com.webmars.webmars_api.dto;

import com.webmars.webmars_api.Run;
import jakarta.validation.constraints.NotNull;


public record LogRunRequest (
        @NotNull(message = "Snippet ID is required")
        Long snippetId,
        Integer durationMs,
        Integer instructionsExecuted,
        @NotNull(message = "Exit status is required")
        Run.ExitStatus exitStatus,
        String errorMessage
) {}