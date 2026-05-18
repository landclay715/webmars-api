package com.webmars.webmars_api.dto;

import com.webmars.webmars_api.RunRepository.MostRunProjection;
import java.time.LocalDateTime;

public record MostRunResponse(
     Long snippetId,
     String snippetTitle,
     Long runCount,
     LocalDateTime lastRun
) {
    public static MostRunResponse from(MostRunProjection p){
        return new MostRunResponse(
                p.getSnippetId(),
                p.getTitle(),
                p.getRunCount(),
                p.getLastRun()
        );
    }
}
