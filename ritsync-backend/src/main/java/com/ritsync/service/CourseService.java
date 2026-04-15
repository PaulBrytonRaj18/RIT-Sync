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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository    courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final UserService         userService;

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
    public Enrollment enrollStudent(Long courseId, Long studentId) {
        var student = userService.findOrThrow(studentId);
        if (student.getRole() != Role.STUDENT) {
            throw RitSyncException.badRequest("Only students can be enrolled");
        }
        if (enrollmentRepository.existsByStudentIdAndCourseIdAndIsActiveTrue(studentId, courseId)) {
            throw RitSyncException.conflict("Student already enrolled in this course");
        }
        var course = courseRepository.findById(courseId)
                .orElseThrow(() -> RitSyncException.notFound("Course not found: " + courseId));
        var enrollment = Enrollment.builder().student(student).course(course).build();
        return enrollmentRepository.save(enrollment);
    }

    public List<CourseResponse> getStudentCourses(Long studentId) {
        return courseRepository.findEnrolledCoursesByStudentId(studentId).stream()
            .map(c -> CourseResponse.from(c, courseRepository.countEnrolledStudents(c.getId())))
            .toList();
    }

    public List<CourseResponse> getInstructorCourses(Long instructorId) {
        return courseRepository.findByInstructorIdAndIsActiveTrue(instructorId).stream()
            .map(c -> CourseResponse.from(c, courseRepository.countEnrolledStudents(c.getId())))
            .toList();
    }
}
