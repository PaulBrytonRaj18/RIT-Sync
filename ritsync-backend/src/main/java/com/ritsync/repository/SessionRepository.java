package com.ritsync.repository;

import com.ritsync.entity.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SessionRepository extends JpaRepository<Session, Long> {
    List<Session> findByCourseIdOrderByStartTimeDesc(Long courseId);
    List<Session> findByIsOpenTrue();
    List<Session> findByIsOpenTrueAndCourseIdIn(List<Long> courseIds);

    @Query("SELECT s FROM Session s WHERE s.course.id = :courseId AND s.startTime BETWEEN :from AND :to")
    List<Session> findByCourseAndDateRange(Long courseId, LocalDateTime from, LocalDateTime to);
}
