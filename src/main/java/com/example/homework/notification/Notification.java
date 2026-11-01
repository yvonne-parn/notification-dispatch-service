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

    private static final int MAX_FAILURE_REASON_LENGTH = 255;

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

    public static Notification sent(NotificationType type, String target, String payload, Instant now) {
        return new Notification(type, target, payload, NotificationStatus.SENT, null, now);
    }
    
    public static Notification failed(NotificationType type, String target, String payload, String reason, Instant now) {
        return new Notification(type, target, payload, NotificationStatus.FAILED, truncate(reason), now);
    }

    private Notification(NotificationType type, String target, String payload, NotificationStatus status, String failureReason, Instant now) {
        this.type = type;
        this.target = target;
        this.payload = payload;
        this.status = status;
        this.failureReason = failureReason;
        this.createdAt = now;
        this.updatedAt = now;
    }

    private static String truncate(String reason) {
        if (reason == null || reason.length() <= MAX_FAILURE_REASON_LENGTH) {
            return reason;
        }
        return reason.substring(0, MAX_FAILURE_REASON_LENGTH);
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
