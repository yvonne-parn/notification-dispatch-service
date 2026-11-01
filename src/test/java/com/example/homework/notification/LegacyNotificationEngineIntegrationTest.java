package com.example.homework.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.example.homework.notification.sender.EmailSender;

@SpringBootTest 
public class LegacyNotificationEngineIntegrationTest {

  @Autowired
  private LegacyNotificationEngine engine;

  @MockitoSpyBean
  private EmailSender emailSender;

  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void clearTable() { 
    jdbcTemplate.update("delete from ntf_tx"); 
  }

  @Test
  public void emailSuccessIsStoredAsSent() throws Exception {
    LegacyNotificationResponse response = engine.processNotification("EMAIL", "buyer@example.com", "hello");

    assertThat(response.getStatus()).isEqualTo("S");
    assertThat(response.getDispatchCount()).isEqualTo(1);
    assertThat(response.getFailureCount()).isZero();
    assertThat(engine.getHistory()).hasSize(1);
  }

  @Test
  public void blockedTargetFailsBeforeDispatchIsInserted() {
    try {
      engine.processNotification("SMS", "blocked-user@example.com", "hello");
      fail("Expected blocked target to fail");
    } catch (RuntimeException ex) {
      assertThat(ex.getMessage()).isEqualTo("Business Rule Violation: User is blacklisted or rate-limited by DB policy.");
    } catch (Exception ex) {
      fail("Expected RuntimeException but got: " + ex.getClass().getName());
    }

    assertThat(engine.getHistory()).isEmpty();
  }

  @Test
  public void unsupportedTypeIsStoredAsUnknown() {
    try {
      engine.processNotification("FAX", "ops@example.com", "hello");
      fail("Expected unsupported type to fail");
    } catch (Exception ex) {
      assertThat(ex.getMessage()).isEqualTo("Unsupported type: FAX");
    }

    LegacyNotificationResponse latest = engine.getLatestState();
    assertThat(latest.getStatus()).isEqualTo("U");
    assertThat(latest.getMessage()).isEqualTo("Unsupported type: FAX");
  }

  @Test
  public void threeFailuresOpenCircuitBreakerForNextAttempt() {
    doThrow(new RuntimeException("SMTP Timeout")).when(emailSender).send(any(), any());

    for (int i = 0; i < 3; i++) {
      try {
        engine.processNotification("EMAIL", "failing-user@example.com", "boom");
        fail("Expected email failure");
      } catch (RuntimeException ex) {
        assertThat(ex.getMessage()).isEqualTo("SMTP Timeout");
      } catch (Exception ex) {
        fail("Expected RuntimeException but got: " + ex.getClass().getName());
      }
    }

    try {
      engine.processNotification("SMS", "ok-user@example.com", "hello");
      fail("Expected circuit breaker to reject the request");
    } catch (RuntimeException ex) {
      assertThat(ex.getMessage()).isEqualTo("Circuit breaker OPEN via database state.");
    } catch (Exception ex) {
      fail("Expected RuntimeException but got: " + ex.getClass().getName());
    }

    LegacyNotificationResponse latest = engine.getLatestState();
    assertThat(latest.getStatus()).isEqualTo("R");
    assertThat(latest.getFailureCount()).isEqualTo(3);
    assertThat(latest.getDispatchCount()).isEqualTo(4);
  }
}