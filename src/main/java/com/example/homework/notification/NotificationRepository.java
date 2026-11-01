package com.example.homework.notification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    long countByTargetAndCreatedAtAfter(String target, Instant since);

    List<Notification> findByTypeOrderByIdDesc(NotificationType type, Pageable pageable);

    Optional<Notification> findFirstByOrderByIdDesc();

    List<Notification> findAllBy(Pageable pageable);
}
