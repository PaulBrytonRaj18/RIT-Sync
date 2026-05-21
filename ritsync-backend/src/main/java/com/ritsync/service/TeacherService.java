package com.ritsync.service;

import com.ritsync.dto.response.*;
import com.ritsync.entity.*;
import com.ritsync.enums.AttendanceStatus;
import com.ritsync.exception.RitSyncException;
import com.ritsync.repository.*;
import lombok.RequiredArgsConstructor;
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
        long totalSessions = sessionRepo.findByCourseIdOrderByStartTimeDesc(courseId).size();

        List<SubjectAttendanceSummaryResponse.StudentSummary> summaries = enrollments.stream().map(e -> {
            User s = e.getStudent();
            long present = attendanceRepo.countByStudentAndCourseAndStatus(s.getId(), courseId, AttendanceStatus.PRESENT);
            long absent  = attendanceRepo.countByStudentAndCourseAndStatus(s.getId(), courseId, AttendanceStatus.ABSENT);
            long late    = attendanceRepo.countByStudentAndCourseAndStatus(s.getId(), courseId, AttendanceStatus.LATE);
            long excused = attendanceRepo.countByStudentAndCourseAndStatus(s.getId(), courseId, AttendanceStatus.EXCUSED);
            double pct = totalSessions == 0 ? 0.0 : (present + late) * 100.0 / totalSessions;
            return new SubjectAttendanceSummaryResponse.StudentSummary(
                s.getId(), s.getName(), s.getRollNumber(),
                present, absent, late, excused, pct, pct < 75.0
            );
        }).sorted(Comparator.comparingDouble(
            (SubjectAttendanceSummaryResponse.StudentSummary ss) -> ss.percentage()
        )).toList();

        return new SubjectAttendanceSummaryResponse(courseId, course.getName(), course.getCode(), totalSessions, summaries);
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

        List<Enrollment> allEnrollments = enrollmentRepo.findByCourseIdInAndIsActiveTrue(courseIds);
        Set<Long> allStudentIds = allEnrollments.stream()
            .map(e -> e.getStudent().getId())
            .collect(Collectors.toSet());

        Map<Long, List<Long>> studentCourses = new HashMap<>();
        for (Enrollment e : allEnrollments) {
            studentCourses.computeIfAbsent(e.getStudent().getId(), k -> new ArrayList<>()).add(e.getCourse().getId());
        }

        LocalDate today = LocalDate.now();
        long todayAbsentees = 0;
        List<ClassDashboardResponse.DefaulterSummary> defaulters = new ArrayList<>();
        List<ClassDashboardResponse.CourseAttendanceSummary> courseSummaries = new ArrayList<>();

        Map<Long, Double> studentOverallPct = new HashMap<>();
        Map<Long, Map<Long, Double>> studentCoursePct = new HashMap<>();

        for (Long sid : allStudentIds) {
            double totalPctSum = 0;
            int courseCount = 0;
            Map<Long, Double> coursePcts = new HashMap<>();

            List<Long> sCourses = studentCourses.getOrDefault(sid, List.of());
            for (Long cid : sCourses) {
                long total = attendanceRepo.countTotalSessionsByStudentAndCourse(sid, cid);
                if (total == 0) continue;
                long present = attendanceRepo.countByStudentAndCourseAndStatus(sid, cid, AttendanceStatus.PRESENT);
                long late = attendanceRepo.countByStudentAndCourseAndStatus(sid, cid, AttendanceStatus.LATE);
                double pct = (present + late) * 100.0 / total;
                coursePcts.put(cid, pct);
                totalPctSum += pct;
                courseCount++;
            }
            double overall = courseCount == 0 ? 0 : totalPctSum / courseCount;
            studentOverallPct.put(sid, overall);
            studentCoursePct.put(sid, coursePcts);

            if (overall < 75.0 && overall > 0) {
                List<ClassDashboardResponse.CourseDeficit> deficits = coursePcts.entrySet().stream()
                    .map(e -> {
                        Course matched = courses.stream().filter(co -> co.getId().equals(e.getKey())).findFirst().orElse(null);
                        return new ClassDashboardResponse.CourseDeficit(e.getKey(), matched != null ? matched.getName() : "Unknown", e.getValue());
                    })
                    .toList();
                User student = userRepo.findById(sid).orElse(null);
                if (student != null) {
                    defaulters.add(new ClassDashboardResponse.DefaulterSummary(
                        sid, student.getName(), student.getRollNumber(), overall, deficits
                    ));
                }
            }

            boolean isAbsentToday = hasAbsentToday(sid, sCourses);
            if (isAbsentToday) todayAbsentees++;
        }

        for (Course course : courses) {
            List<Enrollment> enr = enrollmentRepo.findByCourseIdAndIsActiveTrue(course.getId());
            if (enr.isEmpty()) continue;
            double avgPct = enr.stream()
                .mapToDouble(e -> studentOverallPct.getOrDefault(e.getStudent().getId(), 0.0))
                .average().orElse(0);
            courseSummaries.add(new ClassDashboardResponse.CourseAttendanceSummary(
                course.getId(), course.getName(), course.getCode(),
                enr.size(), avgPct
            ));
        }

        defaulters.sort(Comparator.comparingDouble(ClassDashboardResponse.DefaulterSummary::overallPct));

        double overallPct = allStudentIds.isEmpty() ? 0 :
            allStudentIds.stream().mapToDouble(id -> studentOverallPct.getOrDefault(id, 0.0)).average().orElse(0);

        return new ClassDashboardResponse(
            allStudentIds.size(), overallPct, todayAbsentees, defaulters.size(),
            courses.size(), courseSummaries, defaulters
        );
    }

    private boolean hasAbsentToday(Long studentId, List<Long> courseIds) {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        for (Long cid : courseIds) {
            List<Session> todaySessions = sessionRepo.findByCourseAndDateRange(cid, start, end);
            for (Session s : todaySessions) {
                if (Boolean.TRUE.equals(s.getIsOpen())) {
                    var record = attendanceRepo.findBySessionIdAndStudentId(s.getId(), studentId);
                    if (record.isEmpty() || record.get().getStatus() == AttendanceStatus.ABSENT) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Transactional
    public LeaveRequest createLeaveRequest(Long studentId, Long courseId, String leaveType,
                                           String reason, LocalDate fromDate, LocalDate toDate) {
        User student = userRepo.findById(studentId)
            .orElseThrow(() -> RitSyncException.notFound("Student not found"));
        Course course = courseRepo.findById(courseId)
            .orElseThrow(() -> RitSyncException.notFound("Course not found"));

        LeaveRequest lr = LeaveRequest.builder()
            .student(student)
            .course(course)
            .leaveType(LeaveRequest.LeaveType.valueOf(leaveType))
            .reason(reason)
            .fromDate(fromDate)
            .toDate(toDate)
            .status(LeaveRequest.LeaveStatus.PENDING)
            .build();
        lr = leaveRequestRepo.save(lr);
        auditService.log(studentId, student.getEmail(), "LEAVE_REQUESTED",
            "leave_requests", lr.getId(), null, null);
        return lr;
    }

    @Transactional
    public LeaveRequest reviewLeaveRequest(Long leaveId, Long reviewerId, String decision, String notes) {
        LeaveRequest lr = leaveRequestRepo.findById(leaveId)
            .orElseThrow(() -> RitSyncException.notFound("Leave request not found"));
        User reviewer = userRepo.findById(reviewerId)
            .orElseThrow(() -> RitSyncException.notFound("Reviewer not found"));

        lr.setStatus(LeaveRequest.LeaveStatus.valueOf(decision));
        lr.setReviewedBy(reviewer);
        lr.setReviewNotes(notes);
        lr = leaveRequestRepo.save(lr);

        if (decision.equals("APPROVED")) {
            autoAdjustAttendanceOnLeave(lr);
        }

        auditService.log(reviewerId, reviewer.getEmail(), "LEAVE_" + decision,
            "leave_requests", lr.getId(), null, null);
        return lr;
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

    public List<LeaveRequest> getPendingLeaves(Long teacherId) {
        List<Course> courses = courseRepo.findByInstructorIdAndIsActiveTrue(teacherId);
        List<Long> courseIds = courses.stream().map(Course::getId).toList();
        return courseIds.stream()
            .flatMap(cid -> leaveRequestRepo.findByCourseIdAndStatusOrderByCreatedAtDesc(cid, LeaveRequest.LeaveStatus.PENDING).stream())
            .toList();
    }

    public List<LeaveRequest> getStudentLeaveHistory(Long studentId) {
        return leaveRequestRepo.findByStudentIdOrderByCreatedAtDesc(studentId);
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
