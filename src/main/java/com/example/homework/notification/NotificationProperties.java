package com.example.homework.notification;

import java.time.Duration;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;

@Validated
@ConfigurationProperties(prefix = "notification")
public record NotificationProperties(RateLimit rateLimit, CircuitBreaker circuitBreaker, Set<String> blockedTargets) {

    public NotificationProperties {
        blockedTargets = blockedTargets == null ? Set.of() : Set.copyOf(blockedTargets);
    }

    public record RateLimit(Duration window, @Min(1) int maxAttempts) {}

    public record CircuitBreaker(Duration cooldown, @Min(1) int failureThreshold) {}
}