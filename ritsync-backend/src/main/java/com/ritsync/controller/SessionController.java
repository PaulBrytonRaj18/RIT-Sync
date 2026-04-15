package com.ritsync.controller;

import com.ritsync.dto.request.CreateSessionRequest;
import com.ritsync.dto.response.ApiResponse;
import com.ritsync.dto.response.SessionResponse;
import com.ritsync.security.UserPrincipal;
import com.ritsync.service.SessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<SessionResponse> createSession(
            @Valid @RequestBody CreateSessionRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok("Session created",
            sessionService.createSession(req, principal.getId()));
    }

    @GetMapping("/course/{courseId}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<SessionResponse>> getCourseSessions(@PathVariable Long courseId) {
        return ApiResponse.ok(sessionService.getCourseSessions(courseId));
    }

    @GetMapping("/open")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<SessionResponse>> getOpenSessions() {
        return ApiResponse.ok(sessionService.getOpenSessions());
    }

    @GetMapping("/{sessionId}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<SessionResponse> getSession(@PathVariable Long sessionId) {
        return ApiResponse.ok(sessionService.getById(sessionId));
    }

    @PatchMapping("/{sessionId}/open")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<SessionResponse> openSession(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok("Session opened",
            sessionService.openSession(sessionId, principal.getId()));
    }

    @PatchMapping("/{sessionId}/close")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<SessionResponse> closeSession(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok("Session closed",
            sessionService.closeSession(sessionId, principal.getId()));
    }
}
