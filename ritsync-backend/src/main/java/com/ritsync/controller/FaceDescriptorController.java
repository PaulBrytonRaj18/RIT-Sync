package com.ritsync.controller;

import com.ritsync.dto.response.ApiResponse;
import com.ritsync.security.UserPrincipal;
import com.ritsync.service.FaceDescriptorService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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

    public record EnrollFaceRequest(
        @NotNull @Size(min = 128, max = 128) List<Double> descriptor,
        // Optional client-side encrypted copy (kept for the client's own use; not used for matching)
        @Size(max = 8192) String encryptedDescriptor,
        @Size(max = 64)   String encryptionIv,
        @Size(max = 64)   String salt
    ) {}

    @PostMapping("/me/face")
    @PreAuthorize("hasRole('STUDENT')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> enrollFace(
            @Valid @RequestBody EnrollFaceRequest body,
            @AuthenticationPrincipal UserPrincipal principal) {
        faceDescriptorService.enroll(principal.getId(), body.descriptor(),
            body.encryptedDescriptor(), body.encryptionIv(), body.salt());
        return ApiResponse.ok("Face ID enrolled successfully", null);
    }

    @GetMapping("/me/face/status")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<Map<String, Boolean>> faceStatus(
            @AuthenticationPrincipal UserPrincipal principal) {
        boolean enrolled = faceDescriptorService.isEnrolled(principal.getId());
        return ApiResponse.ok(Map.of("enrolled", enrolled));
    }

    // The former GET /users/me/face/descriptor endpoint was removed on purpose:
    // stored biometric templates must never leave the server.
}
