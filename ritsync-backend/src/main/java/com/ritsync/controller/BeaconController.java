package com.ritsync.controller;

import com.ritsync.service.BeaconVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/beacons")
@RequiredArgsConstructor
public class BeaconController {

    private final BeaconVerificationService beaconService;

    @PostMapping("/generate/{sessionId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ResponseEntity<?> generateBeacon(@PathVariable Long sessionId) {
        return ResponseEntity.ok(beaconService.generateOrUpdateBeacon(sessionId));
    }

    @GetMapping("/session/{sessionId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ResponseEntity<?> getSessionBeacon(@PathVariable Long sessionId) {
        return beaconService.getBeaconForSession(sessionId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
