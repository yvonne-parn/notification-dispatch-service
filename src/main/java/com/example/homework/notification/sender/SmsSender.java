package com.example.homework.notification.sender;

import org.springframework.stereotype.Component;

import com.example.homework.notification.NotificationType;

@Component
public class SmsSender implements NotificationSender {

    @Override
    public NotificationType type() {
        return NotificationType.SMS;
    }

    @Override
    public void send(String target, String payload) {
        // Simulate sending an SMS
    }
}
