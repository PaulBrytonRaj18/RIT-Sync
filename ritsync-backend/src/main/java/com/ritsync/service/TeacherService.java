package com.ritsync.service;

import com.ritsync.dto.response.*;
import com.ritsync.entity.*;
import com.ritsync.enums.AttendanceStatus;
import com.ritsync.exception.RitSyncException;
import com.ritsync.repository.*;
import com.ritsync.security.AccessGuard;
import com.ritsync.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TeacherService {

    private final CourseRepository           courseRepo;
    private final EnrollmentRepository       enrollmentRepo;
    private final SessionRepository          sessionRepo;
    private final AttendanceRecordRepository attendanceRepo;
    private final UserRepository             userRepo;
    private final LeaveRequestRepository     leaveRequestRepo;
    private final AuditService               auditService;
    private final AccessGuard                guard;
    private final AttendanceStatsService     statsService;

    @Value("${app.attendance.threshold-warning-percent:75}")
    private double warningThreshold;

    @Transactional(readOnly = true)
    public TeacherDashboardResponse getSubjectTeacherDashboard(Long teacherId) {
        List<Course> courses = courseRepo.findByInstructorIdAndIsActiveTrue(teacherId);
        List<Long> courseIds = courses.stream().map(Course::getId).toList();

        Map<Long, Integer> enrollmentCounts = getEnrollmentCounts(courseIds);
        Map<Long, List<Session>> sessionsByCourse = getSessionsByCourse(courseIds);

        LocalDateTime now = LocalDateTime.now();
        List<TeacherDashboardResponse.SessionTile> todaySessions = new ArrayList<>();
        List<TeacherDashboardResponse.SessionTile> pendingSessions = new ArrayList<>();

        for (Course course : courses) {
            List<Session> courseSessions = sessionsByCourse.getOrDefault(course.getId(), List.of());
            for (Session s : courseSessions) {
                boolean isToday = s.getStartTime().toLocalDate().equals(now.toLocalDate());
                if (isToday) {
                    todaySessions.add(new TeacherDashboardResponse.SessionTile(
                        s.getId(), course.getId(), course.getName(), course.getCode(),
                        s.getTitle(), s.getStartTime().toString(), s.getEndTime().toString(),
                        Boolean.TRUE.equals(s.getIsOpen())
                    ));
                }
                boolean started = !s.getStartTime().isAfter(now);
                boolean notOpened = !Boolean.TRUE.equals(s.getIsOpen());
                if (isToday && started && notOpened) {
                    pendingSessions.add(new TeacherDashboardResponse.SessionTile(
                        s.getId(), course.getId(), course.getName(), course.getCode(),
                        s.getTitle(), s.getStartTime().toString(), s.getEndTime().toString(),
                        false
                    ));
                }
            }
        }

        List<TeacherDashboardResponse.HandledSection> handledSections = courses.stream()
            .map(c -> new TeacherDashboardResponse.HandledSection(
                c.getId(), c.getCode(), c.getName(), c.getDepartment(),
                enrollmentCounts.getOrDefault(c.getId(), 0)
            ))
            .toList();

        List<TeacherDashboardResponse.CourseTile> courseTiles = courses.stream()
            .map(c -> new TeacherDashboardResponse.CourseTile(
                c.getId(), c.getCode(), c.getName(), c.getDepartment(),
                enrollmentCounts.getOrDefault(c.getId(), 0)
            ))
            .toList();

        int liveSessions = (int) sessionsByCourse.values().stream()
            .flatMap(Collection::stream)
            .filter(s -> Boolean.TRUE.equals(s.getIsOpen()))
            .count();

        int totalStudents = enrollmentCounts.values().stream().mapToInt(Integer::intValue).sum();

        return new TeacherDashboardResponse(
            courseTiles, todaySessions, pendingSessions, handledSections,
            totalStudents, liveSessions
        );
    }

    @Transactional(readOnly = true)
    public SubjectAttendanceSummaryResponse getSubjectAttendanceView(Long courseId, Long teacherId) {
        Course course = courseRepo.findById(courseId)
            .orElseThrow(() -> RitSyncException.notFound("Course not found: " + courseId));
        if (!course.getInstructor().getId().equals(teacherId)) {
            throw RitSyncException.forbidden("You are not the instructor for this course.");
        }

        List<Enrollment> enrollments = enrollmentRepo.findByCourseIdAndIsActiveTrue(courseId);
        LocalDateTime now = LocalDateTime.now();
        long sessionsHeld = sessionRepo.findByCourseIdOrderByStartTimeDesc(courseId).stream()
            .filter(s -> !s.getEndTime().isAfter(now))
            .count();
        Map<Long, AttendanceStatsService.Counts> counts =
            statsService.forCourses(List.of(courseId)).getOrDefault(courseId, Map.of());

        List<SubjectAttendanceSummaryResponse.StudentSummary> summaries = enrollments.stream().map(e -> {
            User s = e.getStudent();
            var c = counts.getOrDefault(s.getId(), AttendanceStatsService.Counts.EMPTY);
            return new SubjectAttendanceSummaryResponse.StudentSummary(
                s.getId(), s.getName(), s.getRollNumber(),
                c.present(), c.absent(), c.late(), c.excused(), c.percentage(),
                c.hasData() && c.percentage() < warningThreshold);
        }).sorted(Comparator.comparingDouble(SubjectAttendanceSummaryResponse.StudentSummary::percentage))
          .toList();

        return new SubjectAttendanceSummaryResponse(courseId, course.getName(), course.getCode(), sessionsHeld, summaries);
    }

    @Transactional(readOnly = true)
    public List<AttendanceRecordResponse> getStudentHistoryInCourse(Long studentId, Long courseId, Long teacherId) {
        Course course = courseRepo.findById(courseId)
            .orElseThrow(() -> RitSyncException.notFound("Course not found: " + courseId));
        if (!course.getInstructor().getId().equals(teacherId)) {
            throw RitSyncException.forbidden("You are not the instructor for this course.");
        }
        return attendanceRepo.findByStudentId(studentId).stream()
            .filter(r -> r.getSession().getCourse().getId().equals(courseId))
            .map(AttendanceRecordResponse::from)
            .sorted((a, b) -> b.markedAt() != null && a.markedAt() != null
                ? b.markedAt().compareTo(a.markedAt()) : 0)
            .toList();
    }

    @Transactional(readOnly = true)
    public ClassDashboardResponse getClassTeacherDashboard(Long teacherId) {
        List<Course> courses = courseRepo.findByInstructorIdAndIsActiveTrue(teacherId);
        List<Long> courseIds = courses.stream().map(Course::getId).toList();
        if (courseIds.isEmpty()) {
            return new ClassDashboardResponse(0, 0, 0, 0, 0, List.of(), List.of());
        }
        Map<Long, Course> courseById = courses.stream().collect(Collectors.toMap(Course::getId, c -> c));

        List<Enrollment> enrollments = enrollmentRepo.findByCourseIdInAndIsActiveTrue(courseIds);
        Map<Long, User> students = new LinkedHashMap<>();
        Map<Long, List<Long>> enrolledByCourse = new HashMap<>();
        for (Enrollment e : enrollments) {
            students.putIfAbsent(e.getStudent().getId(), e.getStudent());
            enrolledByCourse.computeIfAbsent(e.getCourse().getId(), k -> new ArrayList<>()).add(e.getStudent().getId());
        }

        // courseId -> studentId -> counts (one query)
        Map<Long, Map<Long, AttendanceStatsService.Counts>> stats = statsService.forCourses(courseIds);

        // Per-student overall % = mean of their course percentages (courses with data only)
        Map<Long, Double> overallByStudent = new HashMap<>();
        List<ClassDashboardResponse.DefaulterSummary> defaulters = new ArrayList<>();
        for (User student : students.values()) {
            List<ClassDashboardResponse.CourseDeficit> perCourse = new ArrayList<>();
            for (Long cid : courseIds) {
                var c = stats.getOrDefault(cid, Map.of()).get(student.getId());
                if (c != null && c.hasData()) {
                    perCourse.add(new ClassDashboardResponse.CourseDeficit(cid, courseById.get(cid).getName(), c.percentage()));
                }
            }
            if (perCourse.isEmpty()) continue; // no sessions held yet: neither good nor bad
            double overall = perCourse.stream().mapToDouble(ClassDashboardResponse.CourseDeficit::percentage).average().orElse(0);
            overallByStudent.put(student.getId(), overall);
            if (overall < warningThreshold) {
                defaulters.add(new ClassDashboardResponse.DefaulterSummary(
                    student.getId(), student.getName(), student.getRollNumber(), overall,
                    perCourse.stream().filter(d -> d.percentage() < warningThreshold).toList()));
            }
        }
        defaulters.sort(Comparator.comparingDouble(ClassDashboardResponse.DefaulterSummary::overallPct));

        // Course averages use each student's percentage IN THAT COURSE
        List<ClassDashboardResponse.CourseAttendanceSummary> courseSummaries = new ArrayList<>();
        for (Course course : courses) {
            List<Long> enrolled = enrolledByCourse.getOrDefault(course.getId(), List.of());
            if (enrolled.isEmpty()) continue;
            double avg = stats.getOrDefault(course.getId(), Map.of()).values().stream()
                .filter(AttendanceStatsService.Counts::hasData)
                .mapToDouble(AttendanceStatsService.Counts::percentage)
                .average().orElse(0);
            courseSummaries.add(new ClassDashboardResponse.CourseAttendanceSummary(
                course.getId(), course.getName(), course.getCode(), enrolled.size(), avg));
        }

        double overallPct = overallByStudent.values().stream().mapToDouble(Double::doubleValue).average().orElse(0);

        return new ClassDashboardResponse(
            students.size(), overallPct, countAbsentToday(courseIds), defaulters.size(),
            courses.size(), courseSummaries, defaulters);
    }

    /** Distinct students marked ABSENT in any of today's sessions that have already started. */
    private long countAbsentToday(List<Long> courseIds) {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        LocalDateTime now = LocalDateTime.now();
        List<Long> todaysSessionIds = new ArrayList<>();
        for (Long cid : courseIds) {
            sessionRepo.findByCourseAndDateRange(cid, start, start.plusDays(1)).stream()
                .filter(s -> !s.getStartTime().isAfter(now))
                .forEach(s -> todaysSessionIds.add(s.getId()));
        }
        if (todaysSessionIds.isEmpty()) return 0;
        return attendanceRepo.findStatusesForSessions(todaysSessionIds).stream()
            .filter(row -> row[2] == AttendanceStatus.ABSENT)
            .map(row -> row[1])
            .distinct()
            .count();
    }

    private static final int MAX_LEAVE_DAYS = 60;

    @Transactional
    public LeaveRequestResponse createLeaveRequest(Long studentId, Long courseId, String leaveType,
                                                   String reason, LocalDate fromDate, LocalDate toDate) {
        User student = userRepo.findById(studentId)
            .orElseThrow(() -> RitSyncException.notFound("Student not found"));
        Course course = courseRepo.findById(courseId)
            .orElseThrow(() -> RitSyncException.notFound("Course not found"));
        if (!enrollmentRepo.existsByStudentIdAndCourseIdAndIsActiveTrue(studentId, courseId)) {
            throw RitSyncException.forbidden("You are not enrolled in this course.");
        }
        LeaveRequest.LeaveType type = parseEnum(LeaveRequest.LeaveType.class, leaveType, "leave type");
        if (reason == null || reason.isBlank() || reason.length() > 1000) {
            throw RitSyncException.badRequest("Reason is required (max 1000 characters).");
        }
        if (toDate.isBefore(fromDate)) {
            throw RitSyncException.badRequest("Leave end date must not be before its start date.");
        }
        if (fromDate.plusDays(MAX_LEAVE_DAYS).isBefore(toDate)) {
            throw RitSyncException.badRequest("A single leave request cannot exceed " + MAX_LEAVE_DAYS + " days.");
        }

        LeaveRequest lr = LeaveRequest.builder()
            .student(student)
            .course(course)
            .leaveType(type)
            .reason(reason.trim())
            .fromDate(fromDate)
            .toDate(toDate)
            .status(LeaveRequest.LeaveStatus.PENDING)
            .build();
        lr = leaveRequestRepo.save(lr);
        auditService.log(studentId, student.getEmail(), "LEAVE_REQUESTED",
            "leave_requests", lr.getId(), null, null);
        return LeaveRequestResponse.from(lr);
    }

    @Transactional
    public LeaveRequestResponse reviewLeaveRequest(Long leaveId, UserPrincipal reviewerPrincipal,
                                                   String decision, String notes) {
        LeaveRequest lr = leaveRequestRepo.findById(leaveId)
            .orElseThrow(() -> RitSyncException.notFound("Leave request not found"));
        if (lr.getCourse() == null) {
            throw RitSyncException.badRequest("Leave request is not linked to a course.");
        }
        guard.requireManage(lr.getCourse(), reviewerPrincipal);
        if (lr.getStatus() != LeaveRequest.LeaveStatus.PENDING) {
            throw RitSyncException.conflict("This leave request has already been " + lr.getStatus().name().toLowerCase() + ".");
        }
        LeaveRequest.LeaveStatus status = parseEnum(LeaveRequest.LeaveStatus.class, decision, "decision");
        if (status == LeaveRequest.LeaveStatus.PENDING) {
            throw RitSyncException.badRequest("Decision must be APPROVED or REJECTED.");
        }
        User reviewer = userRepo.findById(reviewerPrincipal.getId())
            .orElseThrow(() -> RitSyncException.notFound("Reviewer not found"));

        lr.setStatus(status);
        lr.setReviewedBy(reviewer);
        lr.setReviewNotes(notes);
        lr = leaveRequestRepo.save(lr);

        if (status == LeaveRequest.LeaveStatus.APPROVED) {
            autoAdjustAttendanceOnLeave(lr);
        }

        auditService.log(reviewer.getId(), reviewer.getEmail(), "LEAVE_" + status.name(),
            "leave_requests", lr.getId(), null, null);
        return LeaveRequestResponse.from(lr);
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String label) {
        try {
            return Enum.valueOf(type, value == null ? "" : value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw RitSyncException.badRequest("Invalid " + label + ": " + value);
        }
    }

    private void autoAdjustAttendanceOnLeave(LeaveRequest lr) {
        LocalDateTime from = lr.getFromDate().atStartOfDay();
        LocalDateTime to = lr.getToDate().plusDays(1).atStartOfDay();
        List<Session> sessions = sessionRepo.findByCourseAndDateRange(lr.getCourse().getId(), from, to);
        for (Session s : sessions) {
            var existing = attendanceRepo.findBySessionIdAndStudentId(s.getId(), lr.getStudent().getId());
            existing.ifPresent(rec -> {
                if (rec.getStatus() == AttendanceStatus.ABSENT) {
                    rec.setStatus(AttendanceStatus.EXCUSED);
                    rec.setManuallyOverridden(true);
                    rec.setOverrideReason("Leave approved: " + lr.getLeaveType());
                    attendanceRepo.save(rec);
                }
            });
        }
    }

    @Transactional(readOnly = true)
    public List<LeaveRequestResponse> getPendingLeaves(Long teacherId) {
        List<Course> courses = courseRepo.findByInstructorIdAndIsActiveTrue(teacherId);
        return courses.stream()
            .flatMap(c -> leaveRequestRepo.findByCourseIdAndStatusOrderByCreatedAtDesc(c.getId(), LeaveRequest.LeaveStatus.PENDING).stream())
            .map(LeaveRequestResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<LeaveRequestResponse> getStudentLeaveHistory(Long studentId) {
        return leaveRequestRepo.findByStudentIdOrderByCreatedAtDesc(studentId).stream()
            .map(LeaveRequestResponse::from)
            .toList();
    }

    private Map<Long, Integer> getEnrollmentCounts(List<Long> courseIds) {
        if (courseIds.isEmpty()) return Map.of();
        Map<Long, Integer> result = new HashMap<>();
        enrollmentRepo.findByCourseIdInAndIsActiveTrue(courseIds).forEach(e ->
            result.merge(e.getCourse().getId(), 1, Integer::sum));
        return result;
    }

    private Map<Long, List<Session>> getSessionsByCourse(List<Long> courseIds) {
        if (courseIds.isEmpty()) return Map.of();
        Map<Long, List<Session>> map = new HashMap<>();
        for (Long cid : courseIds) {
            map.put(cid, sessionRepo.findByCourseIdOrderByStartTimeDesc(cid));
        }
        return map;
    }
}
