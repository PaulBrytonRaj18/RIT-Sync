package com.ritsync.controller;

import com.ritsync.dto.response.*;
import com.ritsync.entity.LeaveRequest;
import com.ritsync.security.UserPrincipal;
import com.ritsync.service.TeacherService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/teacher")
@RequiredArgsConstructor
public class TeacherController {

    private final TeacherService teacherService;

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<TeacherDashboardResponse> getDashboard(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(teacherService.getSubjectTeacherDashboard(principal.getId()));
    }

    @GetMapping("/courses/{courseId}/attendance")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<SubjectAttendanceSummaryResponse> getSubjectAttendance(
            @PathVariable Long courseId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(teacherService.getSubjectAttendanceView(courseId, principal.getId()));
    }

    @GetMapping("/courses/{courseId}/students/{studentId}/history")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<List<AttendanceRecordResponse>> getStudentHistory(
            @PathVariable Long courseId,
            @PathVariable Long studentId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(teacherService.getStudentHistoryInCourse(studentId, courseId, principal.getId()));
    }

    @GetMapping("/class-dashboard")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<ClassDashboardResponse> getClassDashboard(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(teacherService.getClassTeacherDashboard(principal.getId()));
    }

    @PostMapping("/leaves")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<LeaveRequest> requestLeave(
            @RequestParam Long courseId,
            @RequestParam String leaveType,
            @RequestParam String reason,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok("Leave requested",
            teacherService.createLeaveRequest(principal.getId(), courseId, leaveType, reason, fromDate, toDate));
    }

    @GetMapping("/leaves/pending")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<List<LeaveRequest>> getPendingLeaves(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(teacherService.getPendingLeaves(principal.getId()));
    }

    @PutMapping("/leaves/{leaveId}/review")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<LeaveRequest> reviewLeave(
            @PathVariable Long leaveId,
            @RequestParam String decision,
            @RequestParam(required = false) String notes,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok("Leave " + decision.toLowerCase(),
            teacherService.reviewLeaveRequest(leaveId, principal.getId(), decision, notes));
    }

    @GetMapping("/leaves/my")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<List<LeaveRequest>> getMyLeaves(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(teacherService.getStudentLeaveHistory(principal.getId()));
    }
}
