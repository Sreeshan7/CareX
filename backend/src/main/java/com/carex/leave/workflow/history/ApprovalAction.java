package com.carex.leave.workflow.history;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

/** One row per transition — the request timeline. Unique index: one APPROVE/REJECT per (request, stage). */
@Entity
@Immutable
@Table(name = "approval_action")
public class ApprovalAction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Column(nullable = false)
    private String stage;

    @Column(nullable = false)
    private String action;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "actor_capacity", nullable = false)
    private String actorCapacity;

    @Column(name = "from_status")
    private String fromStatus;

    @Column(name = "to_status", nullable = false)
    private String toStatus;

    private String comment;

    @Column(name = "conflict_acknowledged", nullable = false)
    private boolean conflictAcknowledged;

    @Column(nullable = false)
    private String channel;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ApprovalAction() {}

    public ApprovalAction(Long requestId, String stage, String action, Long actorId, String actorCapacity,
                          String fromStatus, String toStatus, String comment, boolean conflictAcknowledged,
                          String channel, Instant createdAt) {
        this.requestId = requestId;
        this.stage = stage;
        this.action = action;
        this.actorId = actorId;
        this.actorCapacity = actorCapacity;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.comment = comment;
        this.conflictAcknowledged = conflictAcknowledged;
        this.channel = channel;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getRequestId() { return requestId; }
    public String getStage() { return stage; }
    public String getAction() { return action; }
    public Long getActorId() { return actorId; }
    public String getActorCapacity() { return actorCapacity; }
    public String getFromStatus() { return fromStatus; }
    public String getToStatus() { return toStatus; }
    public String getComment() { return comment; }
    public boolean isConflictAcknowledged() { return conflictAcknowledged; }
    public String getChannel() { return channel; }
    public Instant getCreatedAt() { return createdAt; }
}
