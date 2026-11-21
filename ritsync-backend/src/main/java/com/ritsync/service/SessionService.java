package com.ritsync.service;

import com.ritsync.dto.request.CreateSessionRequest;
import com.ritsync.dto.response.SessionResponse;
import com.ritsync.entity.AttendanceRecord;
import com.ritsync.entity.Course;
import com.ritsync.entity.Enrollment;
import com.ritsync.entity.Session;
import com.ritsync.enums.AttendanceStatus;
import com.ritsync.exception.RitSyncException;
import com.ritsync.repository.AttendanceRecordRepository;
import com.ritsync.repository.CourseRepository;
import com.ritsync.repository.EnrollmentRepository;
import com.ritsync.repository.SessionRepository;
import com.ritsync.security.AccessGuard;
import com.ritsync.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private static final int MIN_RADIUS_M = 10;
    private static final int MAX_RADIUS_M = 1000;

    private final SessionRepository          sessionRepo;
    private final CourseRepository           courseRepo;
    private final EnrollmentRepository       enrollmentRepo;
    private final AttendanceRecordRepository attendanceRepo;
    private final AuditService               auditService;
    private final AccessGuard                guard;

    @Transactional
    public SessionResponse createSession(CreateSessionRequest req, UserPrincipal actor) {
        Course course = guard.requireManage(req.courseId(), actor);

        if (!req.endTime().isAfter(req.startTime())) {
            throw RitSyncException.badRequest("Session end time must be after its start time.");
        }
        int radius = req.geofenceRadiusM() != null ? req.geofenceRadiusM() : 100;
        if (radius < MIN_RADIUS_M || radius > MAX_RADIUS_M) {
            throw RitSyncException.badRequest(
                "Geofence radius must be between " + MIN_RADIUS_M + " and " + MAX_RADIUS_M + " metres.");
        }

        Session session = Session.builder()
            .course(course)
            .title(req.title())
            .startTime(req.startTime())
            .endTime(req.endTime())
            .geofenceLat(req.geofenceLat())
            .geofenceLng(req.geofenceLng())
            .geofenceRadiusM(radius)
            .isOpen(false)
            .enableLiveness(Boolean.TRUE.equals(req.enableLiveness()))
            .enableBle(Boolean.TRUE.equals(req.enableBle()))
            // ZKP verification is not implemented; never let a session claim it.
            .enableZkp(false)
            .isMakeup(Boolean.TRUE.equals(req.isMakeup()))
            .build();

        session = sessionRepo.save(session);
        seedAbsentRecords(session);

        auditService.log(actor.getId(), actor.getEmail(), "SESSION_CREATED",
            "sessions", session.getId(), null, null);

        log.info("Session {} created for course {} by user {}", session.getId(), course.getCode(), actor.getId());
        return SessionResponse.from(session);
    }

    private void seedAbsentRecords(Session session) {
        List<Enrollment> enrollments = enrollmentRepo.findByCourseIdAndIsActiveTrue(session.getCourse().getId());
        List<AttendanceRecord> records = enrollments.stream()
            .map(e -> AttendanceRecord.builder()
                .session(session)
                .student(e.getStudent())
                .status(AttendanceStatus.ABSENT)
                .build())
            .toList();
        attendanceRepo.saveAll(records);
        log.debug("Seeded {} absent records for session {}", records.size(), session.getId());
    }

    @Transactional
    public SessionResponse openSession(Long sessionId, UserPrincipal actor) {
        return setOpen(sessionId, actor, true);
    }

    @Transactional
    public SessionResponse closeSession(Long sessionId, UserPrincipal actor) {
        return setOpen(sessionId, actor, false);
    }

    private SessionResponse setOpen(Long sessionId, UserPrincipal actor, boolean open) {
        Session session = guard.requireManageSession(sessionId, actor);
        session.setIsOpen(open);
        sessionRepo.save(session);
        auditService.log(actor.getId(), actor.getEmail(), open ? "SESSION_OPENED" : "SESSION_CLOSED",
            "sessions", sessionId, null, null);
        return SessionResponse.from(session);
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> getCourseSessions(Long courseId, UserPrincipal actor) {
        guard.requireView(courseId, actor);
        return sessionRepo.findByCourseIdOrderByStartTimeDesc(courseId)
            .stream().map(SessionResponse::from).toList();
    }

    /**
     * Open sessions relevant to the caller: admins see all, teachers see their own courses',
     * students see only courses they are enrolled in.
     */
    @Transactional(readOnly = true)
    public List<SessionResponse> getOpenSessions(UserPrincipal actor) {
        if (AccessGuard.isAdmin(actor)) {
            return sessionRepo.findByIsOpenTrue().stream().map(SessionResponse::from).toList();
        }
        List<Course> courses = AccessGuard.isStudent(actor)
            ? courseRepo.findEnrolledCoursesByStudentId(actor.getId())
            : courseRepo.findByInstructorIdAndIsActiveTrue(actor.getId());
        List<Long> courseIds = courses.stream().map(Course::getId).toList();
        if (courseIds.isEmpty()) return List.of();
        return sessionRepo.findByIsOpenTrueAndCourseIdIn(courseIds)
            .stream().map(SessionResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public SessionResponse getById(Long sessionId, UserPrincipal actor) {
        return SessionResponse.from(guard.requireViewSession(sessionId, actor));
    }
}
