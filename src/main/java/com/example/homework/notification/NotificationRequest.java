package com.example.homework.notification;

import java.util.Locale;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record NotificationRequest(
  @NotNull NotificationType type,
  @NotBlank(message = "Target is mandatory") @Size(max = 255) String target,
  @NotBlank @Size(max = 2000) String payload) {

    public NotificationRequest {
      if (target != null) {
          target = target.trim();
          if (type == NotificationType.EMAIL) {
              target = target.toLowerCase(Locale.ROOT);
          }
        }
    }
}