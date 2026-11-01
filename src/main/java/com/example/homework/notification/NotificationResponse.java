package com.example.homework.notification;

import java.time.Instant;

public record NotificationResponse(
  Long id,
  NotificationType type,
  String target,
  NotificationStatus status,
  String failureReason,
  Instant createdAt,
  Instant updatedAt
) {
  public static NotificationResponse from(Notification notification) {
    return new NotificationResponse(notification.getId(), notification.getType(), notification.getTarget(), notification.getStatus(), notification.getFailureReason(), notification.getCreatedAt(), notification.getUpdatedAt());
  }
}