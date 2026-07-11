package com.webmars.webmars_api;

import com.webmars.webmars_api.dto.SaveSnippetRequest;
import com.webmars.webmars_api.dto.SnippetResponse;
import com.webmars.webmars_api.dto.UpdateSnippetRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/snippets")
public class SnippetController {

    private final SnippetRepository snippets;
    private final UserRepository users;

    public SnippetController(SnippetRepository snippets, UserRepository users) {
        this.snippets = snippets;
        this.users = users;
    }

    @GetMapping("/{id}")
    public SnippetResponse get(@PathVariable Long id, @AuthenticationPrincipal String username) {
        Snippet s = snippets.findByIdWithOwner(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (s.getVisibility() == Snippet.Visibility.PRIVATE) {
            // 404 not 403 — don't reveal that a private snippet exists.
            if (username == null || s.getOwner() == null || !s.getOwner().getUsername().equals(username)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            }
        }
        return SnippetResponse.from(s);
    }

    @GetMapping("/mine")
    public Page<SnippetResponse> mine(
            @AuthenticationPrincipal String username,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        User me = users.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return snippets.findByOwnerIdWithOwner(me.getId(), PageRequest.of(page, Math.min(size, 100)))
                .map(SnippetResponse::from);
    }

    @GetMapping("/public")
    public Page<SnippetResponse> publicFeed(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return snippets.findByVisibilityWithOwner(
                        Snippet.Visibility.PUBLIC,
                        PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "updatedAt")))
                .map(SnippetResponse::from);
    }

    @PostMapping("/save")
    public SnippetResponse save(@Valid @RequestBody SaveSnippetRequest req,
                                @AuthenticationPrincipal String username) {
        User owner = users.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        Snippet s = new Snippet();
        s.setTitle(req.title());
        s.setCode(req.code());
        s.setOwner(owner);
        s.setVisibility(req.visibility() == null ? Snippet.Visibility.PRIVATE : req.visibility());
        return SnippetResponse.from(snippets.save(s));
    }

    @PutMapping("/{id}")
    public SnippetResponse update(@PathVariable Long id,
                                  @Valid @RequestBody UpdateSnippetRequest req,
                                  @AuthenticationPrincipal String username) {
        Snippet s = ownedOr404(id, username);
        if (req.title() != null) s.setTitle(req.title());
        if (req.code() != null) s.setCode(req.code());
        if (req.visibility() != null) s.setVisibility(req.visibility());
        return SnippetResponse.from(snippets.save(s));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal String username) {
        snippets.delete(ownedOr404(id, username));
    }

    private Snippet ownedOr404(Long id, String username) {
        Snippet s = snippets.findByIdWithOwner(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (username == null || s.getOwner() == null || !s.getOwner().getUsername().equals(username)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return s;
    }
}
