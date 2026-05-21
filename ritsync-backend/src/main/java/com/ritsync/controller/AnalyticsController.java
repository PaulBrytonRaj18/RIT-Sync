package com.ritsync.controller;

import com.ritsync.service.HeatmapAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final HeatmapAnalyticsService heatmapService;

    @GetMapping("/heatmap/{sessionId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ResponseEntity<?> getHeatmap(@PathVariable Long sessionId) {
        return ResponseEntity.ok(heatmapService.getSessionHeatmap(sessionId));
    }
}
