package com.webmars.webmars_api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SnippetRepository extends JpaRepository<Snippet, Long> {

    /**
     * Fetch-join variants: the owner association is LAZY and
     * spring.jpa.open-in-view is false, so any code path that reads
     * owner fields after the repository call returns MUST load the
     * owner eagerly here — otherwise Jackson / the ownership checks hit
     * a closed session and blow up with LazyInitializationException
     * (the production 500s on GET/DELETE and the populated feeds).
     */
    @Query("select s from Snippet s left join fetch s.owner where s.id = :id")
    Optional<Snippet> findByIdWithOwner(@Param("id") Long id);

    @Query(
            value = "select s from Snippet s join fetch s.owner where s.owner.id = :ownerId order by s.updatedAt desc",
            countQuery = "select count(s) from Snippet s where s.owner.id = :ownerId")
    Page<Snippet> findByOwnerIdWithOwner(@Param("ownerId") Long ownerId, Pageable pageable);

    @Query(
            value = "select s from Snippet s left join fetch s.owner where s.visibility = :visibility",
            countQuery = "select count(s) from Snippet s where s.visibility = :visibility")
    Page<Snippet> findByVisibilityWithOwner(@Param("visibility") Snippet.Visibility visibility, Pageable pageable);
}
