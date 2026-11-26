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
    boolean existsByStudentIdAndFaceDescriptorHash(Long studentId, String faceDescriptorHash);

    // ── Attendance statistics ────────────────────────────────────────────
    // A record "counts" once its session has ended, or earlier if the student already has a
    // non-ABSENT status (so an ongoing class doesn't show everyone as absent).

    @Query("""
        SELECT a.status, COUNT(a) FROM AttendanceRecord a
        WHERE a.student.id = :studentId
          AND a.session.course.id = :courseId
          AND (a.session.endTime <= :now OR a.status <> :absent)
        GROUP BY a.status
    """)
    List<Object[]> countStatusesForStudent(Long studentId, Long courseId, LocalDateTime now, AttendanceStatus absent);

    @Query("""
        SELECT a.session.course.id, a.student.id, a.status, COUNT(a) FROM AttendanceRecord a
        WHERE a.session.course.id IN :courseIds
          AND (a.session.endTime <= :now OR a.status <> :absent)
        GROUP BY a.session.course.id, a.student.id, a.status
    """)
    List<Object[]> countStatusesPerCourseAndStudent(List<Long> courseIds, LocalDateTime now, AttendanceStatus absent);

    /** Status counts over every course (admin overview). */
    @Query("""
        SELECT a.status, COUNT(a) FROM AttendanceRecord a
        WHERE (a.session.endTime <= :now OR a.status <> :absent)
        GROUP BY a.status
    """)
    List<Object[]> countStatusesOverall(LocalDateTime now, AttendanceStatus absent);

    /** Status counts for sessions that started in [from, to). */
    @Query("""
        SELECT a.status, COUNT(a) FROM AttendanceRecord a
        WHERE a.session.startTime >= :from AND a.session.startTime < :to
          AND (a.session.endTime <= :now OR a.status <> :absent)
        GROUP BY a.status
    """)
    List<Object[]> countStatusesForSessionsStartingBetween(LocalDateTime from, LocalDateTime to,
                                                           LocalDateTime now, AttendanceStatus absent);

    /** Status counts grouped by the STUDENT's department. */
    @Query("""
        SELECT a.student.department, a.status, COUNT(a) FROM AttendanceRecord a
        WHERE (a.session.endTime <= :now OR a.status <> :absent)
        GROUP BY a.student.department, a.status
    """)
    List<Object[]> countStatusesByStudentDepartment(LocalDateTime now, AttendanceStatus absent);

    @Query("""
        SELECT a.session.id, a.student.id, a.status FROM AttendanceRecord a
        WHERE a.session.id IN :sessionIds
    """)
    List<Object[]> findStatusesForSessions(List<Long> sessionIds);
}
