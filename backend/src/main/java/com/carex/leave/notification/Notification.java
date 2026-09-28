package com.carex.leave.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "notification")
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_id", nullable = false)
    private Long recipientId;

    @Column(nullable = false)
    private String type;

    @Column(name = "request_id")
    private Long requestId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String body;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Notification() {}

    public Notification(Long recipientId, String type, Long requestId, String title, String body, Instant createdAt) {
        this.recipientId = recipientId;
        this.type = type;
        this.requestId = requestId;
        this.title = title;
        this.body = body;
        this.createdAt = createdAt;
    }

    public void markRead(Instant at) {
        if (readAt == null) readAt = at;
    }

    public Long getId() { return id; }
    public Long getRecipientId() { return recipientId; }
    public String getType() { return type; }
    public Long getRequestId() { return requestId; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public Instant getReadAt() { return readAt; }
    public Instant getCreatedAt() { return createdAt; }
}
