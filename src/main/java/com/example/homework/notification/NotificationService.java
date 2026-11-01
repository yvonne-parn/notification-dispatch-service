package com.example.homework.notification;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.example.homework.notification.sender.NotificationSender;

@Service
public class NotificationService {
    
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository repository;
    private final Map<NotificationType, NotificationSender> senders;
    private final NotificationProperties properties;
    private final Clock clock;

    public NotificationService(NotificationRepository repository, List<NotificationSender> senderList, Clock clock, NotificationProperties properties) {
        this.repository = repository;
        this.senders = senderList.stream().collect(Collectors.toMap(NotificationSender::type, Function.identity()));
        for (NotificationType type : NotificationType.values()) {
            if (!senders.containsKey(type)) {
                throw new IllegalStateException("No sender registered for " + type);
            }
        }
        this.properties = properties;
        this.clock = clock;
    }

    public NotificationResponse getLatestState() {
        return repository.findFirstByOrderByIdDesc()
                .map(NotificationResponse::from)
                .orElseThrow(() -> new NotificationException(HttpStatus.NOT_FOUND, "No notifications yet"));
    }

    public List<NotificationResponse> getHistory(int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, Notification::getId));
        return repository.findAllBy(pageRequest).stream().map(NotificationResponse::from).toList();
    }

    public NotificationResponse send(NotificationRequest request) {
        NotificationType type = request.type();
        String target = request.target();

        rejectIfBlocked(target);
        rejectIfRateLimited(target);
        rejectIfCircuitOpen(type);

        NotificationSender sender = senders.get(type);

        try {
            sender.send(target, request.payload());
        } catch (RuntimeException e) {
            repository.save(Notification.failed(type, target, request.payload(), e.getMessage(), clock.instant()));
            log.warn("{} dispatch failed", type, e);
            throw new NotificationException(HttpStatus.BAD_GATEWAY, type + " dispatch failed");
        }

        Notification saved = repository.save(Notification.sent(type, target, request.payload(), clock.instant()));
        log.info("Notification {} sent via {}", saved.getId(), type);
        return NotificationResponse.from(saved);
    }

    private void rejectIfBlocked(String target) {
         if (properties.blockedTargets().contains(target)) {
            throw new NotificationException(HttpStatus.FORBIDDEN, "Recipient is blocked");
        }
    }

    private void rejectIfRateLimited(String target) {
        Instant since = clock.instant().minus(properties.rateLimit().window());
        if (repository.countByTargetAndCreatedAtAfter(target, since) >= properties.rateLimit().maxAttempts()) {
            throw new NotificationException(HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded for this recipient");
        }
    }

    private void rejectIfCircuitOpen(NotificationType type) {
        int threshold = properties.circuitBreaker().failureThreshold();
        Instant cooldownStart = clock.instant().minus(properties.circuitBreaker().cooldown());
        List<Notification> latest = repository.findByTypeOrderByIdDesc(type, PageRequest.of(0, threshold));

        boolean failedInARow = latest.size() == threshold && latest.stream().allMatch(n -> n.getStatus() == NotificationStatus.FAILED);
        if (failedInARow && latest.get(0).getCreatedAt().isAfter(cooldownStart)) {
            throw new NotificationException(HttpStatus.SERVICE_UNAVAILABLE, type + " channel is temporarily unavailable after repeated failures");
        }
    }
}
