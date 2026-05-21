package com.ritsync.service;

import com.ritsync.dto.request.CreateSessionRequest;
import com.ritsync.dto.response.SessionResponse;
import com.ritsync.entity.Course;
import com.ritsync.entity.Enrollment;
import com.ritsync.entity.Session;
import com.ritsync.entity.AttendanceRecord;
import com.ritsync.enums.AttendanceStatus;
import com.ritsync.exception.RitSyncException;
import com.ritsync.repository.AttendanceRecordRepository;
import com.ritsync.repository.CourseRepository;
import com.ritsync.repository.EnrollmentRepository;
import com.ritsync.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final SessionRepository          sessionRepo;
    private final CourseRepository           courseRepo;
    private final EnrollmentRepository       enrollmentRepo;
    private final AttendanceRecordRepository attendanceRepo;
    private final AuditService              auditService;

    @Transactional
    public SessionResponse createSession(CreateSessionRequest req, Long instructorId) {
        Course course = courseRepo.findById(req.courseId())
            .orElseThrow(() -> RitSyncException.notFound("Course not found: " + req.courseId()));

        if (!course.getInstructor().getId().equals(instructorId)) {
            throw RitSyncException.forbidden("You are not the instructor for this course.");
        }

        Session session = Session.builder()
            .course(course)
            .title(req.title())
            .startTime(req.startTime())
            .endTime(req.endTime())
            .geofenceLat(req.geofenceLat())
            .geofenceLng(req.geofenceLng())
            .geofenceRadiusM(req.geofenceRadiusM() != null ? req.geofenceRadiusM() : 100)
            .isOpen(false)
            .enableLiveness(Boolean.TRUE.equals(req.enableLiveness()))
            .enableBle(Boolean.TRUE.equals(req.enableBle()))
            .enableZkp(Boolean.TRUE.equals(req.enableZkp()))
            .isMakeup(Boolean.TRUE.equals(req.isMakeup()))
            .build();

        session = sessionRepo.save(session);

        seedAbsentRecords(session);

        auditService.log(instructorId, null, "SESSION_CREATED",
            "sessions", session.getId(), null, null);

        log.info("Session {} created for course {} by instructor {}", session.getId(), course.getCode(), instructorId);
        return SessionResponse.from(session);
    }

    private void seedAbsentRecords(Session session) {
        List<Enrollment> enrollments = enrollmentRepo.findByCourseIdAndIsActiveTrue(session.getCourse().getId());
        for (Enrollment e : enrollments) {
            AttendanceRecord record = AttendanceRecord.builder()
                .session(session)
                .student(e.getStudent())
                .status(AttendanceStatus.ABSENT)
                .faceVerified(false)
                .gpsVerified(false)
                .deviceVerified(false)
                .build();
            attendanceRepo.save(record);
        }
        log.debug("Seeded {} absent records for session {}", enrollments.size(), session.getId());
    }

    @Transactional
    public SessionResponse openSession(Long sessionId, Long instructorId) {
        Session session = getAndValidateOwner(sessionId, instructorId);
        session.setIsOpen(true);
        sessionRepo.save(session);
        auditService.log(instructorId, null, "SESSION_OPENED", "sessions", sessionId, null, null);
        return SessionResponse.from(session);
    }

    @Transactional
    public SessionResponse closeSession(Long sessionId, Long instructorId) {
        Session session = getAndValidateOwner(sessionId, instructorId);
        session.setIsOpen(false);
        sessionRepo.save(session);
        auditService.log(instructorId, null, "SESSION_CLOSED", "sessions", sessionId, null, null);
        return SessionResponse.from(session);
    }

    @Transactional
    public List<SessionResponse> getCourseSessions(Long courseId) {
        return sessionRepo.findByCourseIdOrderByStartTimeDesc(courseId)
            .stream().map(SessionResponse::from).toList();
    }

    @Transactional
    public List<SessionResponse> getOpenSessions() {
        return sessionRepo.findByIsOpenTrue()
            .stream().map(SessionResponse::from).toList();
    }

    @Transactional
    public SessionResponse getById(Long sessionId) {
        return sessionRepo.findById(sessionId)
            .map(SessionResponse::from)
            .orElseThrow(() -> RitSyncException.notFound("Session not found: " + sessionId));
    }

    private Session getAndValidateOwner(Long sessionId, Long instructorId) {
        Session session = sessionRepo.findById(sessionId)
            .orElseThrow(() -> RitSyncException.notFound("Session not found: " + sessionId));
        if (!session.getCourse().getInstructor().getId().equals(instructorId)) {
            throw RitSyncException.forbidden("You are not authorised to manage this session.");
        }
        return session;
    }
}
