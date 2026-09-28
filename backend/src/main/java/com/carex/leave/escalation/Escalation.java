package com.carex.leave.escalation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** One row per (request, stage) — UNIQUE constraint makes escalation exactly-once (implementation.md §11.2). */
@Entity
@Table(name = "escalation")
public class Escalation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Column(nullable = false)
    private String stage;

    @Column(name = "deadline_at", nullable = false)
    private Instant deadlineAt;

    @Column(name = "escalated_at", nullable = false)
    private Instant escalatedAt;

    @Column(name = "escalated_to_user_id")
    private Long escalatedToUserId;

    @Column(name = "escalated_to_role")
    private String escalatedToRole;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    private String resolution;

    protected Escalation() {}

    public Escalation(Long requestId, String stage, Instant deadlineAt, Instant escalatedAt, Long escalatedToUserId,
                      String escalatedToRole) {
        this.requestId = requestId;
        this.stage = stage;
        this.deadlineAt = deadlineAt;
        this.escalatedAt = escalatedAt;
        this.escalatedToUserId = escalatedToUserId;
        this.escalatedToRole = escalatedToRole;
    }

    public void resolve(Instant at, String resolution) {
        this.resolvedAt = at;
        this.resolution = resolution;
    }

    public Long getId() { return id; }
    public Long getRequestId() { return requestId; }
    public String getStage() { return stage; }
    public Instant getDeadlineAt() { return deadlineAt; }
    public Instant getEscalatedAt() { return escalatedAt; }
    public Long getEscalatedToUserId() { return escalatedToUserId; }
    public String getEscalatedToRole() { return escalatedToRole; }
    public Instant getResolvedAt() { return resolvedAt; }
    public String getResolution() { return resolution; }
}
