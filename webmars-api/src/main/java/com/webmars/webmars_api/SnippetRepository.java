package com.webmars.webmars_api;

import jakarta.persistence.Entity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SnippetRepository extends JpaRepository<Snippet, Long>{
    Page<Snippet> findByOwnerIdOrderByUpdatedAtDesc(Long ownerId, Pageable pageable);
    @EntityGraph
    Page<Snippet> findByVisibility(Snippet.Visibility visibility, Pageable pageable);
}
