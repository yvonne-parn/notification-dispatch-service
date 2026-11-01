package com.example.homework.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import com.example.homework.notification.NotificationProperties.CircuitBreaker;
import com.example.homework.notification.NotificationProperties.RateLimit;
import com.example.homework.notification.sender.NotificationSender;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-31T12:00:00Z"), ZoneOffset.UTC);
    private NotificationProperties properties = new NotificationProperties(
        new RateLimit(Duration.ofMinutes(1), 3),
        new CircuitBreaker(Duration.ofMinutes(1), 3),
        Set.of("blocked-target@example.com"));

    @Mock private NotificationRepository repository;

    private NotificationService serviceWith(NotificationSender emailSender) {
        List<NotificationSender> senders = List.of(emailSender, senderOf(NotificationType.SMS), senderOf(NotificationType.PUSH));
        return new NotificationService(repository, senders, CLOCK, properties);
    }

    private NotificationSender senderOf(NotificationType type) {
        NotificationSender sender = mock(NotificationSender.class);
        when(sender.type()).thenReturn(type);
        return sender;
    }

    private NotificationRequest requestWith(String target) {
        return new NotificationRequest(NotificationType.EMAIL, target, "payload");
    }

    @Test
    void successfulSendSavesSentRow() {
        String target = "test-target@example.com";
        NotificationSender emailSender = senderOf(NotificationType.EMAIL);

        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificationResponse response = serviceWith(emailSender).send(requestWith(target));
        
        assertEquals(NotificationStatus.SENT, response.status());
        assertEquals(target, response.target());
        assertNull(response.failureReason());
        verify(emailSender).send(target, "payload");
    }

    @Test 
    void failingSenderSavesFailedRowAndThrowsBadGateway() {
        NotificationSender emailSender = senderOf(NotificationType.EMAIL);
        doThrow(new RuntimeException("SMTP Timeout")).when(emailSender).send(any(), any());

        NotificationException ex = assertThrows(NotificationException.class, () -> serviceWith(emailSender).send(requestWith("test-target@example.com")));

        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatus());
        verify(repository).save(argThat((Notification n) ->
                n.getStatus() == NotificationStatus.FAILED
                && "SMTP Timeout".equals(n.getFailureReason())));
    }

    @Test
    void blockedTargetIsRejectedWithoutSavingOrSending() {
        NotificationSender emailSender = senderOf(NotificationType.EMAIL);

        NotificationException ex = assertThrows(NotificationException.class,
                () -> serviceWith(emailSender).send(requestWith("blocked-target@example.com")));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verify(repository, never()).save(any());
        verify(emailSender, never()).send(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"blocked", "unblocked-target@example.com"})
    void targetsThatAreNotExactBlocklistMatchesAreSent(String target) {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificationResponse response = serviceWith(senderOf(NotificationType.EMAIL)).send(requestWith(target));

        assertEquals(NotificationStatus.SENT, response.status());
    }

    @Test
    void targetAtTheRateLimitIsRejectedWithoutSavingOrSending() {
        when(repository.countByTargetAndCreatedAtAfter(any(), any())).thenReturn(3L);

        NotificationSender emailSender = senderOf(NotificationType.EMAIL);

        NotificationException ex = assertThrows(NotificationException.class, () -> serviceWith(emailSender).send(requestWith("test-target@example.com")));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatus());
        verify(repository, never()).save(any());
        verify(emailSender, never()).send(any(), any());
    }

    @Test
    void targetJustBelowTheRateLimitIsAllowed() {
        when(repository.countByTargetAndCreatedAtAfter(any(), any())).thenReturn(2L);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificationResponse response = serviceWith(senderOf(NotificationType.EMAIL)).send(requestWith("target@example.com"));

        assertEquals(NotificationStatus.SENT, response.status());
    }

    @Test
    void rateLimitCountsOnlyAttemptsInsideTheWindow() {
        String target = "target@example.com";
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        serviceWith(senderOf(NotificationType.EMAIL)).send(requestWith(target));

        verify(repository).countByTargetAndCreatedAtAfter(target, CLOCK.instant().minus(Duration.ofMinutes(1)));
    }

    @Test
    void threeFailuresInARowOpensTheCircuit() {
        Instant recent = CLOCK.instant().minusSeconds(10);
        List<Notification> failedNotifications = List.of(
            Notification.failed(NotificationType.EMAIL, "target@example.com", "x", "timeout", recent),
            Notification.failed(NotificationType.EMAIL, "target@example.com", "x", "timeout", recent),
            Notification.failed(NotificationType.EMAIL, "target@example.com", "x", "timeout", recent));
        when(repository.findByTypeOrderByIdDesc(eq(NotificationType.EMAIL), any(Pageable.class))).thenReturn(failedNotifications);

        NotificationException ex = assertThrows(NotificationException.class,
                () -> serviceWith(senderOf(NotificationType.EMAIL)).send(requestWith("test-target@example.com")));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
        verify(repository, never()).save(any());
    }

    @Test
    void aSuccessInTheStreakKeepsTheCircuitClosed() {
        Instant recent = CLOCK.instant().minusSeconds(10);
        List<Notification> notifications = List.of(
            Notification.failed(NotificationType.EMAIL, "target@example.com", "x", "timeout", recent),
            Notification.sent(NotificationType.EMAIL, "target@example.com", "x", recent),
            Notification.failed(NotificationType.EMAIL, "target@example.com", "x", "timeout", recent));
        when(repository.findByTypeOrderByIdDesc(eq(NotificationType.EMAIL), any(Pageable.class))).thenReturn(notifications);

        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificationResponse response = serviceWith(senderOf(NotificationType.EMAIL)).send(requestWith("target@example.com"));

        assertEquals(NotificationStatus.SENT, response.status());
    }

    @Test
    void fewerFailuresThanTheThresholdKeepTheCircuitClosed() {
        Instant recent = CLOCK.instant().minusSeconds(10);
        List<Notification> failedNotifications = List.of(
            Notification.failed(NotificationType.EMAIL, "target@example.com", "x", "timeout", recent),
            Notification.failed(NotificationType.EMAIL, "target@example.com", "x", "timeout", recent));
        when(repository.findByTypeOrderByIdDesc(eq(NotificationType.EMAIL), any(Pageable.class))).thenReturn(failedNotifications);

        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificationResponse response = serviceWith(senderOf(NotificationType.EMAIL)).send(requestWith("target@example.com"));

        assertEquals(NotificationStatus.SENT, response.status());
    }

    @Test
    void circuitClosesOnceTheCooldownHasPassed() {
        Instant old = CLOCK.instant().minus(Duration.ofMinutes(10));
        List<Notification> notifications = List.of(
            Notification.failed(NotificationType.EMAIL, "target@example.com", "x", "timeout", old),
            Notification.failed(NotificationType.EMAIL, "target@example.com", "x", "timeout", old),
            Notification.failed(NotificationType.EMAIL, "target@example.com", "x", "timeout", old));
        when(repository.findByTypeOrderByIdDesc(eq(NotificationType.EMAIL), any(Pageable.class))).thenReturn(notifications);

        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificationResponse response = serviceWith(senderOf(NotificationType.EMAIL)).send(requestWith("target@example.com"));

        assertEquals(NotificationStatus.SENT, response.status());
    }

    @Test
    void openCircuitOnOneChannelDoesNotBlockAnotherChannel() {
        Instant recent = CLOCK.instant().minusSeconds(10);
        List<Notification> notifications = List.of(
            Notification.failed(NotificationType.EMAIL, "target@example.com", "x", "timeout", recent),
            Notification.failed(NotificationType.EMAIL, "target@example.com", "x", "timeout", recent),
            Notification.failed(NotificationType.EMAIL, "target@example.com", "x", "timeout", recent));
        when(repository.findByTypeOrderByIdDesc(eq(NotificationType.EMAIL), any(Pageable.class))).thenReturn(notifications);

        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        NotificationService service = serviceWith(senderOf(NotificationType.EMAIL));

        NotificationException ex = assertThrows(NotificationException.class, () -> service.send(requestWith("test-target@example.com")));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());

        NotificationResponse sms = service.send(new NotificationRequest(NotificationType.SMS, "526391172", "payload"));
        assertEquals(NotificationStatus.SENT, sms.status());
    }

    @Test
    void constructorFailsWhenAChannelHasNoSender() {
        assertThrows(IllegalStateException.class,
                () -> new NotificationService(repository, List.of(senderOf(NotificationType.EMAIL)), CLOCK, properties));
    }
}
