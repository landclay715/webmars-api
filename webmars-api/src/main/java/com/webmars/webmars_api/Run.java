package com.webmars.webmars_api;

import jakarta.persistence.*;
import java.time.LocalDateTime;


@Entity
@Table(name = "runs")
public class Run {

    public enum ExitStatus { COMPLETED, ERROR, PAUSED, ABORTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "snippet_id", nullable = false)
    private Snippet snippet;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "instructions_executed")
    private Integer instructionsExecuted;

    @Enumerated(EnumType.STRING)
    @Column(name = "exit_status", length = 20)
    private ExitStatus exitStatus;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @PrePersist
    void onCreate() { startedAt = LocalDateTime.now(); }

    public Long getId() { return id;}
    public Snippet getSnippet(){ return snippet; }
    public User getUser(){ return user; }
    public LocalDateTime getStartedAt(){ return startedAt; }
    public Integer getDurationMs(){ return durationMs; }
    public Integer getInstructionsExecuted(){ return instructionsExecuted; }
    public ExitStatus getExitStatus() { return exitStatus;}
    public String getErrorMessage() { return errorMessage; }

    public void setSnippet(Snippet snippet) { this.snippet = snippet; }
    public void setUser(User user) { this.user = user; }
    public void setDurationMs(Integer durationMs) { this.durationMs = durationMs; }
    public void setInstructionsExecuted(Integer instructionsExecuted) { this.instructionsExecuted = instructionsExecuted; }
    public void setExitStatus(ExitStatus exitStatus) { this.exitStatus = exitStatus; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
