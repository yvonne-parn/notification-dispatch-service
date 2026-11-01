package com.example.homework.notification.sender;

import org.springframework.stereotype.Component;

import com.example.homework.notification.NotificationType;

@Component
public class PushSender implements NotificationSender {

    @Override
    public NotificationType type() {
        return NotificationType.PUSH;
    }

    @Override
    public void send(String target, String payload) {
        // Simulate sending a push notification
    }
}
