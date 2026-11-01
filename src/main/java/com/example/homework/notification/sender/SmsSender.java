package com.example.homework.notification.sender;

import java.util.logging.Logger;

import org.springframework.stereotype.Component;

import com.example.homework.notification.NotificationType;

@Component
public class SmsSender implements NotificationSender {
    Logger logger = Logger.getLogger(SmsSender.class.getName());

    @Override
    public NotificationType type() {
        return NotificationType.SMS;
    }

    @Override
    public void send(String target, String payload) {
        logger.info("Sending SMS to " + target);
    }
}
