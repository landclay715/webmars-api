package com.webmars.webmars_api;

import  com.webmars.webmars_api.dto.SaveSnippetRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/snippets")
public class SnippetController {

    private final SnippetRepository snippets;
    private final UserRepository users;

    public SnippetController(SnippetRepository snippets, UserRepository users){
    this.snippets = snippets;
    this.users = users;
    }

    @GetMapping("/{id}")
    public Snippet get(@PathVariable Long id, @AuthenticationPrincipal String username){
        Snippet s = snippets.findById(id).orElseThrow(()-> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (s.getVisibility() == Snippet.Visibility.PRIVATE){
        if (username == null || !s.getOwner().getUsername().equals(username)){
            throw new ResponseStatusException((HttpStatus.NOT_FOUND));
            }
        }
    return s;
    }

    @GetMapping("/mine")
    public Page<Snippet> mine(
            @AuthenticationPrincipal String username,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        User me = users.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException((HttpStatus.UNAUTHORIZED)));
        return snippets.findByOwnerIdOrderByUpdatedAtDesc(me.getId(), PageRequest.of(page, Math.min(size, 100)));
    }

    @GetMapping("/public")
    public Page<Snippet> publicFeed(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return snippets.findByVisibility(Snippet.Visibility.PUBLIC, PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "updatedAt")));
    }

    @PostMapping("/save")
    public Snippet save(@Valid @RequestBody SaveSnippetRequest req, @AuthenticationPrincipal String username){
        User owner = users.findByUsername(username).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        Snippet s = new Snippet();
        s.setTitle(req.title());
        s.setCode(req.code());
        s.setOwner(owner);
        s.setVisibility(req.visibility() == null ? Snippet.Visibility.PRIVATE : req.visibility());
        return snippets.save(s);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal String username){
        Snippet s = snippets.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!s.getOwner().getUsername().equals(username)){
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        snippets.delete(s);
    }

    private Snippet ownedOr404(Long id, String username){
        Snippet s = snippets.findById(id).orElseThrow(() -> new ResponseStatusException((HttpStatus.NOT_FOUND)));
        if (username == null || !s.getOwner().getUsername().equals(username)){
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return s;
    }
}