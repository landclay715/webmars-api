package com.webmars.webmars_api;


import com.webmars.webmars_api.dto.LogRunRequest;
import com.webmars.webmars_api.dto.RunResponse;
import com.webmars.webmars_api.dto.MostRunResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/runs")
public class RunController {

    private final RunRepository runs;
    private final SnippetRepository snippets;
    private final UserRepository users;

    public RunController(RunRepository runs, SnippetRepository snippets, UserRepository users){
        this.runs = runs;
        this.snippets = snippets;
        this.users = users;
    }

    @PostMapping
    public RunResponse log(@Valid @RequestBody LogRunRequest req, @AuthenticationPrincipal String username){
        User me = users.findByUsername(username).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        Snippet s = snippets.findById(req.snippetId()).orElseThrow(()-> new ResponseStatusException(HttpStatus.NOT_FOUND));
            if(s.getVisibility() == Snippet.Visibility.PRIVATE && !s.getOwner().getUsername().equals(username)){
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            }
        Run r = new Run();
        r.setUser(me);
        r.setSnippet(s);
        r.setDurationMs(req.durationMs());
        r.setInstructionsExecuted(req.instructionsExecuted());
        r.setExitStatus(req.exitStatus());
        r.setErrorMessage(req.errorMessage());
        return RunResponse.from(runs.save(r));
    }

    @GetMapping("/recent")
    public List<RunResponse> recent(@AuthenticationPrincipal String username, @RequestParam(defaultValue = "20") int limit){
        User me = users.findByUsername(username).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return runs.findByUserIdOrderByStartedAtDesc(me.getId(), PageRequest.of(0, Math.min(limit, 100))).stream().map(RunResponse::from).toList();
    }

    @GetMapping("/most-run")
    public List<MostRunResponse> mostRun(@AuthenticationPrincipal String username, @RequestParam(defaultValue = "10") int limit){
        User me = users.findByUsername(username).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return runs.findMostRunByUser(me.getId(), PageRequest.of(0, Math.min(limit, 100))).stream().map(MostRunResponse::from).toList();
    }
}
