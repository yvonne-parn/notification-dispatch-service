package com.example.homework.notification;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/legacy/notifications")
public class LegacyNotificationController {

  private final LegacyNotificationEngine engine;

  public LegacyNotificationController(LegacyNotificationEngine engine) {
    this.engine = engine;
  }

  @PostMapping("/send")
  public LegacyNotificationResponse send(@RequestBody LegacyNotificationRequest request) throws Exception {
      return engine.processNotification(request.getType(), request.getTarget(), request.getPayload());
  }

  @GetMapping("/history")
  public List<LegacyNotificationResponse> history() { return engine.getHistory(); }

  @GetMapping("/latest")
  public LegacyNotificationResponse latest() { return engine.getLatestState(); }
}