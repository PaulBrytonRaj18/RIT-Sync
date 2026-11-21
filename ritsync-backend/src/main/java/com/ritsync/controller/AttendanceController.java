package com.ritsync.controller;

import com.ritsync.dto.request.ManualOverrideRequest;
import com.ritsync.dto.request.MarkAttendanceRequest;
import com.ritsync.dto.response.ApiResponse;
import com.ritsync.dto.response.AttendanceRecordResponse;
import com.ritsync.dto.response.AttendanceStatsResponse;
import com.ritsync.dto.response.MarkAttendanceResponse;
import com.ritsync.security.UserPrincipal;
import com.ritsync.service.AttendanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;

    @PostMapping("/mark")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<MarkAttendanceResponse> markAttendance(
            @Valid @RequestBody MarkAttendanceRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {

        MarkAttendanceResponse result = attendanceService.markAttendance(principal.getId(), req);
        String message = result.allVerified()
            ? "Attendance marked successfully"
            : "Verification failed — attendance not recorded";
        return ApiResponse.ok(message, result);
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<List<AttendanceRecordResponse>> getMyRecords(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(attendanceService.getMyRecords(principal.getId()));
    }

    @GetMapping("/session/{sessionId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<List<AttendanceRecordResponse>> getSessionAttendance(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(attendanceService.getSessionAttendance(sessionId, principal));
    }

    @GetMapping("/stats/{studentId}/course/{courseId}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<AttendanceStatsResponse> getStudentStats(
            @PathVariable Long studentId,
            @PathVariable Long courseId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(attendanceService.getStudentStats(studentId, courseId, principal));
    }

    @PutMapping("/override/{sessionId}/student/{studentId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<AttendanceRecordResponse> manualOverride(
            @PathVariable Long sessionId,
            @PathVariable Long studentId,
            @Valid @RequestBody ManualOverrideRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {

        return ApiResponse.ok("Attendance overridden",
            attendanceService.manualOverride(sessionId, studentId, req, principal));
    }
}
