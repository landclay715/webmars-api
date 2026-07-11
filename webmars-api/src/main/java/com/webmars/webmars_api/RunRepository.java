package com.webmars.webmars_api;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface RunRepository extends JpaRepository<Run, Long> {

    /**
     * Fetch-join on snippet: RunResponse reads snippet title/id after
     * the repository call, and with open-in-view=false a lazy proxy
     * would throw once rows exist (the production 500 on /runs/recent).
     */
    @Query("""
            SELECT r FROM Run r
            JOIN FETCH r.snippet
            WHERE r.user.id = :userId
            ORDER BY r.startedAt DESC
            """)
    List<Run> findRecentWithSnippetByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("""
            SELECT r.snippet.id AS snippetId,
                   r.snippet.title AS title,
                   COUNT(r)  AS runCount,
                   MAX(r.startedAt) AS lastRun
            FROM Run r
            WHERE r.user.id = :userId
            GROUP BY r.snippet.id, r.snippet.title
            ORDER BY COUNT(r) DESC
            """)
    List<MostRunProjection> findMostRunByUser(@Param("userId") Long userId, Pageable pageable);

    interface MostRunProjection {
        Long getSnippetId();
        String getTitle();
        Long getRunCount();
        LocalDateTime getLastRun();
    }
}
