package com.webmars.webmars_api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SnippetRepository extends JpaRepository<Snippet, Long>{
    List<Snippet> findByOwnerIdOrderByUpdatedAtDesc(Long ownerId);
    Page<Snippet> findByVisibility(Snippet.Visibility visibility, Pageable pageable);
}
