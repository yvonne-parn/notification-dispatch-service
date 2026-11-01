package com.example.homework.notification;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(NotificationController.class)
class NotificationControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean NotificationService service;

    @ParameterizedTest
    @EnumSource(NotificationType.class) 
    void successfulSendReturnsNotification(NotificationType type) throws Exception {
        Instant now = Instant.parse("2026-10-28T12:00:00Z");

        when(service.send(new NotificationRequest(type, "target@example.com", "hello")))
                .thenReturn(new NotificationResponse(1L, type, "target@example.com", NotificationStatus.SENT, null, now, now));
        
        mockMvc.perform(post("/api/notifications/send")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\": \"%s\", \"target\": \"target@example.com\", \"payload\": \"hello\"}".formatted(type)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.type").value(type.name()))
                .andExpect(jsonPath("$.target").value("target@example.com"))
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.failureReason").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.createdAt").value("2026-10-28T12:00:00Z"))
                .andExpect(jsonPath("$.updatedAt").value("2026-10-28T12:00:00Z"));
    }

    @Test
    void sendWithBlankTargetReturns400() throws Exception {
        mockMvc.perform(post("/api/notifications/send")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\": \"EMAIL\", \"target\": \"\", \"payload\": \"hello\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test 
    void sendWithUnknownTypeReturns400() throws Exception {
        mockMvc.perform(post("/api/notifications/send")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\": \"FAX\", \"target\": \"target@example.com\", \"payload\": \"hello\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sendWithMalformedJsonReturns400() throws Exception {
        mockMvc.perform(post("/api/notifications/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sendWithTooLongPayloadReturns400() throws Exception {
        String payload = "x".repeat(2001);

        mockMvc.perform(post("/api/notifications/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\": \"EMAIL\", \"target\": \"target@example.com\", \"payload\": \"%s\"}".formatted(payload)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sendWithBlankPayloadReturns400() throws Exception {
        mockMvc.perform(post("/api/notifications/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\": \"EMAIL\", \"target\": \"target@example.com\", \"payload\": \"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sendWithoutTypeReturns400() throws Exception {
        mockMvc.perform(post("/api/notifications/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\": \"target@example.com\", \"payload\": \"hello\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void historyUsesDefaultsWhenNoParametersGiven() throws Exception {
        when(service.getHistory(0, 20)).thenReturn(List.of());

        mockMvc.perform(get("/api/notifications/history"))
                .andExpect(status().isOk());

        verify(service).getHistory(0, 20);
    }

    @Test
    void historyRaisesSizeBelowOneToOne() throws Exception {
        when(service.getHistory(0, 1)).thenReturn(List.of());

        mockMvc.perform(get("/api/notifications/history").param("size", "0"))
                .andExpect(status().isOk());

        verify(service).getHistory(0, 1);
    }

    @Test
    void historyLimitsPageAndSizeToValidRange() throws Exception {
        when(service.getHistory(0, 100)).thenReturn(List.of());

        mockMvc.perform(get("/api/notifications/history").param("page", "-1").param("size", "5000"))
                .andExpect(status().isOk());

        verify(service).getHistory(0, 100);
    }

    @Test 
    void getLatestStateReturnsLatestNotification() throws Exception {
        Instant now = Instant.parse("2026-10-28T12:00:00Z");
        when(service.getLatestState()).thenReturn(new NotificationResponse(1L, NotificationType.EMAIL, "target@example.com", NotificationStatus.SENT, null, now, now));

        mockMvc.perform(get("/api/notifications/latest"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1L))
            .andExpect(jsonPath("$.type").value("EMAIL"))
            .andExpect(jsonPath("$.target").value("target@example.com"))
            .andExpect(jsonPath("$.status").value("SENT"))
            .andExpect(jsonPath("$.failureReason").value(Matchers.nullValue()))
            .andExpect(jsonPath("$.createdAt").value("2026-10-28T12:00:00Z"))
            .andExpect(jsonPath("$.updatedAt").value("2026-10-28T12:00:00Z"));
    }

    @Test 
    void getLatestStateReturns404WhenNoNotificationsExist() throws Exception {
        when(service.getLatestState()).thenThrow(new NotificationException(HttpStatus.NOT_FOUND, "message"));

        mockMvc.perform(get("/api/notifications/latest")).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @EnumSource(value = HttpStatus.class,
            names = {"FORBIDDEN", "TOO_MANY_REQUESTS", "SERVICE_UNAVAILABLE", "BAD_GATEWAY"})
    void serviceErrorsAreMappedToTheirStatus(HttpStatus errorStatus) throws Exception {
        when(service.send(new NotificationRequest(NotificationType.EMAIL, "target@example.com", "hello")))
                .thenThrow(new NotificationException(errorStatus, "error message"));

        mockMvc.perform(post("/api/notifications/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\": \"EMAIL\", \"target\": \"target@example.com\", \"payload\": \"hello\"}"))
                .andExpect(status().is(errorStatus.value()))
                .andExpect(jsonPath("$.detail").value("error message"));
    }

    @Test
    void sendWithoutTargetFieldReturns400NamingTheField() throws Exception {
        mockMvc.perform(post("/api/notifications/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\": \"EMAIL\", \"payload\": \"hello\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(Matchers.containsString("target")));
    }

    @Test
    void unexpectedErrorReturns500WithoutLeakingDetails() throws Exception {
        when(service.getLatestState()).thenThrow(new RuntimeException("db password is test"));

        mockMvc.perform(get("/api/notifications/latest"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Unexpected error"));
    }
}
