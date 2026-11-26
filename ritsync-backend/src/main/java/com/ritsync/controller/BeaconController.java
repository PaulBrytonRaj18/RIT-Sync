package com.ritsync.controller;

import com.ritsync.dto.response.ApiResponse;
import com.ritsync.entity.Beacon;
import com.ritsync.exception.RitSyncException;
import com.ritsync.security.AccessGuard;
import com.ritsync.security.UserPrincipal;
import com.ritsync.service.BeaconVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

// Context path already adds /api, so this is served at /api/beacons/...
@RestController
@RequestMapping("/beacons")
@RequiredArgsConstructor
public class BeaconController {

    private final BeaconVerificationService beaconService;
    private final AccessGuard guard;

    public record BeaconResponse(Long sessionId, String uuid, String token,
                                 LocalDateTime tokenExpiry, Integer rssiThreshold) {
        static BeaconResponse from(Beacon b) {
            return new BeaconResponse(b.getSession().getId(), b.getUuid(), b.getToken(),
                b.getTokenExpiry(), b.getRssiThreshold());
        }
    }

    @PostMapping("/generate/{sessionId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<BeaconResponse> generateBeacon(@PathVariable Long sessionId,
                                                      @AuthenticationPrincipal UserPrincipal principal) {
        var session = guard.requireManageSession(sessionId, principal);
        return ApiResponse.ok(BeaconResponse.from(beaconService.generateOrUpdateBeacon(session)));
    }

    @GetMapping("/session/{sessionId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<BeaconResponse> getSessionBeacon(@PathVariable Long sessionId,
                                                        @AuthenticationPrincipal UserPrincipal principal) {
        guard.requireManageSession(sessionId, principal);
        return beaconService.getBeaconForSession(sessionId)
            .map(BeaconResponse::from)
            .map(ApiResponse::ok)
            .orElseThrow(() -> RitSyncException.notFound("No active beacon for this session"));
    }
}
