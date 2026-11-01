package com.example.homework.notification;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity 
@Table(name = "notification")
public class Notification {
    @Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Column(nullable = false)
    private String target;

    @Column(nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status;

    private String failureReason;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;
    
    protected Notification() {}

    public Notification(NotificationType type, String target, String payload, NotificationStatus status, String failureReason, Instant now) {
        this.type = type;
        this.target = target;
        this.payload = payload;
        this.status = status;
        this.failureReason = failureReason;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public NotificationType getType() { return type; }
    public String getTarget() { return target; }
    public String getPayload() { return payload; }
    public NotificationStatus getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
