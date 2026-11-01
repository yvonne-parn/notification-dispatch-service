package com.example.homework.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class NotificationRequestTest {

    @Test
    void emailTargetIsTrimmedAndLowercased() {
        NotificationRequest request = new NotificationRequest(NotificationType.EMAIL, "  Test@Example.COM ", "x");

        assertEquals("test@example.com", request.target());
    }

    @ParameterizedTest
    @EnumSource(value = NotificationType.class, names = "EMAIL", mode = EnumSource.Mode.EXCLUDE)
    void otherTargetsAreTrimmedButKeepTheirCase(NotificationType type) {
        NotificationRequest request = new NotificationRequest(type, "  AbC-Token ", "x");

        assertEquals("AbC-Token", request.target());
    }

    @Test
    void nullTargetStaysNullSoValidationCanRejectIt() {
        NotificationRequest request = new NotificationRequest(NotificationType.EMAIL, null, "x");

        assertNull(request.target());
    }
}
