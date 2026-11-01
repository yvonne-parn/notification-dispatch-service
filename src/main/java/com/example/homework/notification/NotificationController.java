package com.example.homework.notification;

import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping("/send")
    public NotificationResponse send(@Valid @RequestBody NotificationRequest request) {        
        return notificationService.send(request);
    }

    @GetMapping("/history")
    public List<NotificationResponse> history(@RequestParam(name = "page", defaultValue = "0") int page, @RequestParam(name = "size", defaultValue = "20") int size) {
        return notificationService.getHistory(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
    }
    
    @GetMapping("/latest")
    public NotificationResponse latest() {
        return notificationService.getLatestState();
    }
}
