package com.ritsync.security;

import com.ritsync.entity.Course;
import com.ritsync.entity.Session;
import com.ritsync.exception.RitSyncException;
import com.ritsync.repository.CourseRepository;
import com.ritsync.repository.EnrollmentRepository;
import com.ritsync.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resource-level authorization (who may touch WHICH course/session), complementing the
 * role checks done by {@code @PreAuthorize}. Rules:
 * <ul>
 *   <li><b>manage</b> a course/session: its instructor, or any ADMIN.</li>
 *   <li><b>view</b> a course/session: a manager, or a student actively enrolled in the course.</li>
 * </ul>
 * Violations raise 403 (or 404 when the resource does not exist).
 */
@Component
@RequiredArgsConstructor
public class AccessGuard {

    private final CourseRepository courseRepository;
    private final SessionRepository sessionRepository;
    private final EnrollmentRepository enrollmentRepository;

    public static boolean isAdmin(UserPrincipal p) {
        return "ADMIN".equals(p.getRole());
    }

    public static boolean isStudent(UserPrincipal p) {
        return "STUDENT".equals(p.getRole());
    }

    // ── Loading helpers ─────────────────────────────────────────────────

    public Course course(Long courseId) {
        return courseRepository.findById(courseId)
            .orElseThrow(() -> RitSyncException.notFound("Course not found: " + courseId));
    }

    public Session session(Long sessionId) {
        return sessionRepository.findById(sessionId)
            .orElseThrow(() -> RitSyncException.notFound("Session not found: " + sessionId));
    }

    // ── Predicates ──────────────────────────────────────────────────────

    public boolean canManage(Course course, UserPrincipal p) {
        return isAdmin(p) || course.getInstructor().getId().equals(p.getId());
    }

    public boolean isEnrolled(Long studentId, Long courseId) {
        return enrollmentRepository.existsByStudentIdAndCourseIdAndIsActiveTrue(studentId, courseId);
    }

    public boolean canView(Course course, UserPrincipal p) {
        return canManage(course, p) || (isStudent(p) && isEnrolled(p.getId(), course.getId()));
    }

    /** Transactional so it can be used outside a request (e.g. from the WebSocket channel). */
    @Transactional(readOnly = true)
    public boolean canManageSession(Long sessionId, UserPrincipal p) {
        return sessionRepository.findById(sessionId)
            .map(s -> canManage(s.getCourse(), p))
            .orElse(false);
    }

    // ── Enforcers ───────────────────────────────────────────────────────

    public Course requireManage(Long courseId, UserPrincipal p) {
        Course course = course(courseId);
        requireManage(course, p);
        return course;
    }

    public void requireManage(Course course, UserPrincipal p) {
        if (!canManage(course, p)) {
            throw RitSyncException.forbidden("You are not the instructor for this course.");
        }
    }

    public Session requireManageSession(Long sessionId, UserPrincipal p) {
        Session session = session(sessionId);
        requireManage(session.getCourse(), p);
        return session;
    }

    public Course requireView(Long courseId, UserPrincipal p) {
        Course course = course(courseId);
        if (!canView(course, p)) {
            throw RitSyncException.forbidden("You do not have access to this course.");
        }
        return course;
    }

    public Session requireViewSession(Long sessionId, UserPrincipal p) {
        Session session = session(sessionId);
        if (!canView(session.getCourse(), p)) {
            throw RitSyncException.forbidden("You do not have access to this session.");
        }
        return session;
    }
}
