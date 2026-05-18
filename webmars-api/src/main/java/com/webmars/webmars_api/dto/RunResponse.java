package com.webmars.webmars_api.dto;

import com.webmars.webmars_api.Run;
import java.time.LocalDateTime;

public record RunResponse(
        Long id,
        Long snippetId,
        String snippetTitle,
        LocalDateTime startedAt,
        Integer durationMs,
        Integer instructionsExecuted,
        Run.ExitStatus exitStatus
) {
    public static RunResponse from(Run r){
        return new RunResponse(
                r.getId(),
                r.getSnippet().getId(),
                r.getSnippet().getTitle(),
                r.getStartedAt(),
                r.getDurationMs(),
                r.getInstructionsExecuted(),
                r.getExitStatus()
        );
    }
}
