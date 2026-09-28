package com.carex.leave.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/** Append-only; a DB trigger rejects UPDATE/DELETE. */
@Entity
@Immutable
@Table(name = "audit_log")
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "actor_role", nullable = false)
    private String actorRole;

    @Column(nullable = false)
    private String channel;

    @Column(nullable = false)
    private String action;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(nullable = false)
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_state")
    private Map<String, Object> beforeState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_state")
    private Map<String, Object> afterState;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column(name = "ip_address")
    private String ipAddress;

    protected AuditLog() {}

    public AuditLog(Instant occurredAt, Long actorId, String actorRole, String channel, String action,
                    String entityType, Long entityId, String summary, Map<String, Object> beforeState,
                    Map<String, Object> afterState, String correlationId, String ipAddress) {
        this.occurredAt = occurredAt;
        this.actorId = actorId;
        this.actorRole = actorRole;
        this.channel = channel;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.summary = summary;
        this.beforeState = beforeState;
        this.afterState = afterState;
        this.correlationId = correlationId;
        this.ipAddress = ipAddress;
    }

    public Long getId() { return id; }
    public Instant getOccurredAt() { return occurredAt; }
    public Long getActorId() { return actorId; }
    public String getActorRole() { return actorRole; }
    public String getChannel() { return channel; }
    public String getAction() { return action; }
    public String getEntityType() { return entityType; }
    public Long getEntityId() { return entityId; }
    public String getSummary() { return summary; }
    public Map<String, Object> getBeforeState() { return beforeState; }
    public Map<String, Object> getAfterState() { return afterState; }
    public String getCorrelationId() { return correlationId; }
    public String getIpAddress() { return ipAddress; }
}
