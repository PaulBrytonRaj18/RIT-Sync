package com.ritsync.controller;

import com.ritsync.dto.response.ApiResponse;
import com.ritsync.security.UserPrincipal;
import com.ritsync.service.FaceDescriptorService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class FaceDescriptorController {

    private final FaceDescriptorService faceDescriptorService;

    @PostMapping("/me/face")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<Void> enrollFace(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserPrincipal principal) {

        @SuppressWarnings("unchecked")
        List<Double> descriptor = (List<Double>) body.get("descriptor");
        if (descriptor == null) {
            return ApiResponse.error("Missing 'descriptor' field");
        }
        faceDescriptorService.enroll(principal.getId(), descriptor);
        return ApiResponse.ok("Face ID enrolled successfully", null);
    }

    @GetMapping("/me/face/status")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<Map<String, Boolean>> faceStatus(
            @AuthenticationPrincipal UserPrincipal principal) {
        boolean enrolled = faceDescriptorService.isEnrolled(principal.getId());
        return ApiResponse.ok(Map.of("enrolled", enrolled));
    }

    @GetMapping("/me/face/descriptor")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<Double>> getFaceDescriptor(
            @AuthenticationPrincipal UserPrincipal principal) {
        List<Double> descriptor = faceDescriptorService.getDescriptor(principal.getId());
        if (descriptor == null) {
            return ApiResponse.error("Face descriptor not found. Please enroll first.");
        }
        return ApiResponse.ok(descriptor);
    }
}
