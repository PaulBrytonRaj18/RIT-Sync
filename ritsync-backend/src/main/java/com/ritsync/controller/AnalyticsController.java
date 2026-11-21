package com.ritsync.controller;

import com.ritsync.dto.response.ApiResponse;
import com.ritsync.security.AccessGuard;
import com.ritsync.security.UserPrincipal;
import com.ritsync.service.HeatmapAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// Context path already adds /api, so this is served at /api/analytics/...
@RestController
@RequestMapping("/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final HeatmapAnalyticsService heatmapService;
    private final AccessGuard guard;

    @GetMapping("/heatmap/{sessionId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Map<String, Object>> getHeatmap(@PathVariable Long sessionId,
                                                       @AuthenticationPrincipal UserPrincipal principal) {
        guard.requireManageSession(sessionId, principal);
        return ApiResponse.ok(heatmapService.getSessionHeatmap(sessionId));
    }
}
