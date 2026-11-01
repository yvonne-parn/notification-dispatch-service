package com.example.homework.notification.sender;

import java.util.logging.Logger;

import org.springframework.stereotype.Component;

import com.example.homework.notification.NotificationType;

@Component 
public class EmailSender implements NotificationSender {
    Logger logger = Logger.getLogger(EmailSender.class.getName());
    
    @Override
    public NotificationType type() {
        return NotificationType.EMAIL;
    }

    @Override
    public void send(String target, String payload) {
        logger.info("Sending EMAIL to " + target);
    }
}
