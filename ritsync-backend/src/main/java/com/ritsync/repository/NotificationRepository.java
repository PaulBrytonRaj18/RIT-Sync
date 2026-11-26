package com.ritsync.repository;

import com.ritsync.entity.Notification;
import com.ritsync.enums.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    boolean existsByUserIdAndCourseIdAndTypeAndCreatedAtAfter(
        Long userId, Long courseId, NotificationType type, LocalDateTime after);

    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);
}
