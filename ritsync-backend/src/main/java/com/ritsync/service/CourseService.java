package com.ritsync.service;

import com.ritsync.dto.request.CreateCourseRequest;
import com.ritsync.dto.response.CourseResponse;
import com.ritsync.entity.Course;
import com.ritsync.entity.Enrollment;
import com.ritsync.enums.Role;
import com.ritsync.exception.RitSyncException;
import com.ritsync.repository.CourseRepository;
import com.ritsync.repository.EnrollmentRepository;
import lombok.RequiredArgsConstructor;
import com.ritsync.entity.AttendanceRecord;
import com.ritsync.enums.AttendanceStatus;
import com.ritsync.repository.AttendanceRecordRepository;
import com.ritsync.repository.SessionRepository;
import com.ritsync.security.AccessGuard;
import com.ritsync.security.UserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository    courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final UserService         userService;
    private final AccessGuard         guard;
    private final SessionRepository   sessionRepository;
    private final AttendanceRecordRepository attendanceRepository;

    @Transactional
    public CourseResponse createCourse(CreateCourseRequest req, Long instructorId) {
        var instructor = userService.findOrThrow(instructorId);
        if (instructor.getRole() != Role.TEACHER && instructor.getRole() != Role.ADMIN) {
            throw RitSyncException.forbidden("Only teachers and admins can create courses");
        }
        var course = Course.builder()
            .code(req.code())
            .name(req.name())
            .instructor(instructor)
            .department(req.department())
            .creditHours(req.creditHours())
            .semester(req.semester())
            .academicYear(req.academicYear())
            .build();
        course = courseRepository.save(course);
        return CourseResponse.from(course, 0);
    }

    @Transactional
    public Enrollment enrollStudent(Long courseId, Long studentId, UserPrincipal actor) {
        var course = guard.requireManage(courseId, actor);
        var student = userService.findOrThrow(studentId);
        if (student.getRole() != Role.STUDENT) {
            throw RitSyncException.badRequest("Only students can be enrolled");
        }
        var existing = enrollmentRepository.findByStudentIdAndCourseId(studentId, courseId);
        if (existing.isPresent() && Boolean.TRUE.equals(existing.get().getIsActive())) {
            throw RitSyncException.conflict("Student already enrolled in this course");
        }
        // Re-enrolling reactivates the old row (the (student, course) pair is unique).
        Enrollment enrollment = existing
            .map(e -> { e.setIsActive(true); return e; })
            .orElseGet(() -> Enrollment.builder().student(student).course(course).build());
        enrollment = enrollmentRepository.save(enrollment);

        // Mid-term enrollment: create ABSENT placeholders for sessions that haven't ended yet,
        // so the student appears in rosters. Sessions that ended before they joined are never counted.
        var now = LocalDateTime.now();
        var records = sessionRepository.findByCourseIdOrderByStartTimeDesc(courseId).stream()
            .filter(s -> s.getEndTime().isAfter(now))
            .filter(s -> attendanceRepository.findBySessionIdAndStudentId(s.getId(), studentId).isEmpty())
            .map(s -> AttendanceRecord.builder().session(s).student(student).status(AttendanceStatus.ABSENT).build())
            .toList();
        attendanceRepository.saveAll(records);
        return enrollment;
    }

    @Transactional(readOnly = true)
    public CourseResponse getById(Long courseId, UserPrincipal actor) {
        Course course = guard.requireView(courseId, actor);
        return CourseResponse.from(course, courseRepository.countEnrolledStudents(courseId));
    }

    @Transactional(readOnly = true)
    public List<CourseResponse> getStudentCourses(Long studentId) {
        List<Course> courses = courseRepository.findEnrolledCoursesByStudentId(studentId);
        List<Long> courseIds = courses.stream().map(Course::getId).toList();
        Map<Long, Integer> counts = getEnrollmentCounts(courseIds);
        return courses.stream()
            .map(c -> CourseResponse.from(c, counts.getOrDefault(c.getId(), 0)))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<CourseResponse> getInstructorCourses(Long instructorId) {
        List<Course> courses = courseRepository.findByInstructorIdAndIsActiveTrue(instructorId);
        List<Long> courseIds = courses.stream().map(Course::getId).toList();
        Map<Long, Integer> counts = getEnrollmentCounts(courseIds);
        return courses.stream()
            .map(c -> CourseResponse.from(c, counts.getOrDefault(c.getId(), 0)))
            .toList();
    }

    private Map<Long, Integer> getEnrollmentCounts(List<Long> courseIds) {
        if (courseIds.isEmpty()) return Map.of();
        Map<Long, Integer> result = new ConcurrentHashMap<>();
        enrollmentRepository.findByCourseIdInAndIsActiveTrue(courseIds).forEach(e ->
            result.merge(e.getCourse().getId(), 1, Integer::sum));
        return result;
    }
}
