package com.example.homework.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class NotificationTest {

    private static final Instant NOW = Instant.parse("2026-10-31T12:00:00Z");

    @Test
    void sentNotificationHasSentStatusAndNoFailureReason() {
        Notification n = Notification.sent(NotificationType.EMAIL, "test@example.com", "hello", NOW);

        assertEquals(NotificationStatus.SENT, n.getStatus());
        assertNull(n.getFailureReason());
        assertEquals(NotificationType.EMAIL, n.getType());
        assertEquals("test@example.com", n.getTarget());
        assertEquals("hello", n.getPayload());
        assertNull(n.getId());
    }

    @Test
    void timestampsComeFromTheGivenInstant() {
        Notification n = Notification.sent(NotificationType.SMS, "+3725551234", "hello", NOW);

        assertEquals(NOW, n.getCreatedAt());
        assertEquals(NOW, n.getUpdatedAt());
    }

    @Test
    void failedNotificationKeepsAShortReason() {
        Notification n = Notification.failed(NotificationType.EMAIL, "test@example.com", "hello", "SMTP Timeout", NOW);

        assertEquals(NotificationStatus.FAILED, n.getStatus());
        assertEquals("SMTP Timeout", n.getFailureReason());
    }

    @Test
    void failedNotificationTruncatesALongReasonTo255Characters() {
        Notification n = Notification.failed(NotificationType.EMAIL, "test@example.com", "hello", "x".repeat(300), NOW);

        assertEquals(255, n.getFailureReason().length());
    }

    @Test
    void failedNotificationWithNullReasonStaysNull() {
        Notification n = Notification.failed(NotificationType.EMAIL, "test@example.com", "hello", null, NOW);

        assertEquals(NotificationStatus.FAILED, n.getStatus());
        assertNull(n.getFailureReason());
    }
}
