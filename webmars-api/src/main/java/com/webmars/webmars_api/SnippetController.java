package com.webmars.webmars_api;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/snippets")
public class SnippetController {

    private final SnippetRepository repository;

    public SnippetController(SnippetRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/{id}")
    public Snippet getSnippet(@PathVariable Long id) {
        return repository.findById(id).orElseThrow();
    }

    @PostMapping("/save")
    public Snippet postSnippet(@RequestBody Snippet snippet) {
        return repository.save(snippet);
    }

    @DeleteMapping("/{id}")
    public void deleteSnippet(@PathVariable Long id){
         repository.deleteById(id);
    }
}
