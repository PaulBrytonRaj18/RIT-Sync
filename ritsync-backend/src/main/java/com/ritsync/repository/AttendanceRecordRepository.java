package com.ritsync.repository;

import com.ritsync.entity.AttendanceRecord;
import com.ritsync.enums.AttendanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {
    Optional<AttendanceRecord> findBySessionIdAndStudentId(Long sessionId, Long studentId);
    List<AttendanceRecord> findBySessionId(Long sessionId);
    List<AttendanceRecord> findByStudentId(Long studentId);

    long countByStatus(AttendanceStatus status);

    long countByMarkedAtBetween(LocalDateTime from, LocalDateTime to);

    @Query("SELECT COUNT(a) FROM AttendanceRecord a WHERE a.markedAt BETWEEN :from AND :to AND a.status = :status")
    long countByMarkedAtBetweenAndStatus(LocalDateTime from, LocalDateTime to, AttendanceStatus status);

    @Query("SELECT COUNT(a) FROM AttendanceRecord a WHERE a.session.course.department = :dept")
    long countByStudentDepartment(String dept);

    @Query("SELECT COUNT(a) FROM AttendanceRecord a WHERE a.session.course.department = :dept AND a.status = :status")
    long countByStudentDepartmentAndStatus(String dept, AttendanceStatus status);

    @Query("""
        SELECT COUNT(a) FROM AttendanceRecord a
        WHERE a.student.id = :studentId
          AND a.session.course.id = :courseId
          AND a.status = :status
    """)
    long countByStudentAndCourseAndStatus(Long studentId, Long courseId, AttendanceStatus status);

    @Query("""
        SELECT COUNT(DISTINCT a.session.id) FROM AttendanceRecord a
        WHERE a.student.id = :studentId
          AND a.session.course.id = :courseId
    """)
    long countTotalSessionsByStudentAndCourse(Long studentId, Long courseId);
}
