package com.example.homework.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.example.homework.notification.sender.EmailSender;

@SpringBootTest(properties = {
    "notification.rate-limit.window=1m",
    "notification.rate-limit.max-attempts=3",
    "notification.circuit-breaker.cooldown=5m",
    "notification.circuit-breaker.failure-threshold=3",
    "notification.blocked-targets=blocked-user@example.com"
})
class NotificationIntegrationTest {
    @Autowired WebApplicationContext context;
    @Autowired NotificationRepository repository;
    @MockitoSpyBean EmailSender emailSender;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        repository.deleteAll();
    }

    private ResultActions send(String type, String target) throws Exception {
        return mockMvc.perform(post("/api/notifications/send")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\": \"%s\", \"target\": \"%s\", \"payload\": \"hello\"}".formatted(type, target)));
    }

    @ParameterizedTest
    @EnumSource(NotificationType.class)
    void everyChannelCanSend(NotificationType type) throws Exception {
        send(type.name(), UUID.randomUUID().toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value(type.name()))
                .andExpect(jsonPath("$.status").value("SENT"));
    }

    @Test
    void sentNotificationShowsUpAsLatestAndInHistory() throws Exception {
        String target = UUID.randomUUID().toString();
        send("EMAIL", target).andExpect(status().isOk());

        mockMvc.perform(get("/api/notifications/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.target").value(target));
        mockMvc.perform(get("/api/notifications/history"))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void emailTargetIsTrimmedAndLowercased() throws Exception {
        send("EMAIL", "  Test@Example.COM ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.target").value("test@example.com"));
    }

    @Test
    void latestReturns404WhenNothingWasSent() throws Exception {
        mockMvc.perform(get("/api/notifications/latest"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No notifications yet"));
    }

    @Test
    void blockedTargetReturns403AndIsNotSaved() throws Exception {
        send("EMAIL", "blocked-user@example.com").andExpect(status().isForbidden());

        assertEquals(0, repository.count());
    }

    @Test
    void fourthSendToTheSameTargetWithinTheWindowReturns429() throws Exception {
        send("EMAIL", "Test@Example.com").andExpect(status().isOk());
        send("EMAIL", "test@example.com").andExpect(status().isOk());
        send("EMAIL", " TEST@example.com ").andExpect(status().isOk());

        send("EMAIL", "test@example.com").andExpect(status().isTooManyRequests());
        send("EMAIL", "other@example.com").andExpect(status().isOk());
    }

    @Test
    void circuitIsOpenAfterThreeRecentFailuresInARowForThatChannelOnly() throws Exception {
        doThrow(new RuntimeException("SMTP Timeout")).when(emailSender).send(any(), any());

        send("EMAIL", "a1@example.com").andExpect(status().isBadGateway());
        send("EMAIL", "a2@example.com").andExpect(status().isBadGateway());
        send("EMAIL", "a3@example.com").andExpect(status().isBadGateway());

        assertEquals(3, repository.count());
        assertTrue(repository.findAll().stream().allMatch(n -> n.getStatus() == NotificationStatus.FAILED));

        send("EMAIL", "a4@example.com").andExpect(status().isServiceUnavailable());
        send("SMS", "a4@example.com").andExpect(status().isOk());
    }

    @Test
    void circuitClosesAgainAfterTheCooldown() throws Exception {
        Instant old = Instant.now().minus(Duration.ofMinutes(10));
        repository.save(Notification.failed(NotificationType.EMAIL, "failed@example.com", "x", "timeout", old));
        repository.save(Notification.failed(NotificationType.EMAIL, "failed@example.com", "x", "timeout", old));
        repository.save(Notification.failed(NotificationType.EMAIL, "failed@example.com", "x", "timeout", old));

        send("EMAIL", "buyer@example.com").andExpect(status().isOk());
    }

    @Test
    void aSuccessBetweenFailuresKeepsTheCircuitClosed() throws Exception {
        Instant now = Instant.now();
        repository.save(Notification.failed(NotificationType.EMAIL, "failed@example.com", "x", "timeout", now));
        repository.save(Notification.sent(NotificationType.EMAIL, "sent@example.com", "x", now));
        repository.save(Notification.failed(NotificationType.EMAIL, "failed@example.com", "x", "timeout", now));

        send("EMAIL", "buyer@example.com").andExpect(status().isOk());
    }

    @Test
    void historyIsNewestFirstAndPagedBy20ByDefault() throws Exception {
        Notification newest = null;
        for (int i = 0; i < 25; i++) {
            newest = repository.save(Notification.sent(NotificationType.EMAIL, "user" + i + "@example.com", "x", Instant.now()));
        }

        mockMvc.perform(get("/api/notifications/history"))
                .andExpect(jsonPath("$.length()").value(20))
                .andExpect(jsonPath("$[0].id").value(newest.getId()));
        mockMvc.perform(get("/api/notifications/history").param("page", "1"))
                .andExpect(jsonPath("$.length()").value(5));
        mockMvc.perform(get("/api/notifications/history").param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test 
    void checkHealth() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }
}
