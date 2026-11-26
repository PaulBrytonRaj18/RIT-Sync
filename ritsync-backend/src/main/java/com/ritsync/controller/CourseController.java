package com.ritsync.controller;

import com.ritsync.dto.request.CreateCourseRequest;
import com.ritsync.dto.response.ApiResponse;
import com.ritsync.dto.response.CourseResponse;
import com.ritsync.security.UserPrincipal;
import com.ritsync.service.CourseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<CourseResponse> createCourse(
            @Valid @RequestBody CreateCourseRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok("Course created", courseService.createCourse(req, principal.getId()));
    }

    @PostMapping("/{courseId}/enroll/{studentId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Void> enrollStudent(@PathVariable Long courseId,
                                           @PathVariable Long studentId,
                                           @AuthenticationPrincipal UserPrincipal principal) {
        courseService.enrollStudent(courseId, studentId, principal);
        return ApiResponse.ok("Student enrolled", null);
    }

    @GetMapping("/{courseId}")
    public ApiResponse<CourseResponse> getCourse(@PathVariable Long courseId,
                                                 @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(courseService.getById(courseId, principal));
    }

    @GetMapping("/my")
    public ApiResponse<List<CourseResponse>> getMyCourses(@AuthenticationPrincipal UserPrincipal principal) {
        boolean isStudent = principal.getRole().equals("STUDENT");
        List<CourseResponse> courses = isStudent
            ? courseService.getStudentCourses(principal.getId())
            : courseService.getInstructorCourses(principal.getId());
        return ApiResponse.ok(courses);
    }
}
