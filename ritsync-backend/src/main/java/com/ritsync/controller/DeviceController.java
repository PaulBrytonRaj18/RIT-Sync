package com.ritsync.controller;

import com.ritsync.dto.response.ApiResponse;
import com.ritsync.entity.DeviceBinding;
import com.ritsync.entity.User;
import com.ritsync.exception.RitSyncException;
import com.ritsync.repository.DeviceBindingRepository;
import com.ritsync.repository.UserRepository;
import com.ritsync.security.UserPrincipal;
import com.ritsync.service.DeviceVerificationService;
import com.ritsync.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Base64;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/devices")
@RequiredArgsConstructor
public class DeviceController {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final DeviceBindingRepository deviceBindingRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final DeviceVerificationService deviceVerificationService;

    private static String generateSecureToken() {
        byte[] tokenBytes = new byte[32];
        SECURE_RANDOM.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<DeviceResponse>> getMyDevices(@AuthenticationPrincipal UserPrincipal principal) {
        List<DeviceBinding> devices = deviceBindingRepository.findByUserId(principal.getId());
        return ApiResponse.ok(devices.stream()
            .map(DeviceResponse::from)
            .collect(Collectors.toList()));
    }

    @PostMapping("/verify/{deviceId}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> sendVerificationEmail(
            @PathVariable Long deviceId,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        DeviceBinding device = deviceBindingRepository.findById(deviceId)
            .orElseThrow(() -> RitSyncException.notFound("Device not found"));
        
        if (!device.getUser().getId().equals(principal.getId())) {
            throw RitSyncException.forbidden("This device does not belong to you");
        }
        
        if (Boolean.TRUE.equals(device.getIsTrusted())) {
            return ApiResponse.ok("Device is already trusted", null);
        }
        
        String token = generateSecureToken();
        device.setVerificationToken(token);
        device.setVerificationExpiry(LocalDateTime.now().plusHours(24));
        deviceBindingRepository.save(device);
        
        User user = device.getUser();
        emailService.sendDeviceVerificationEmail(user.getEmail(), user.getName(), device.getDeviceName(), token);
        
        return ApiResponse.ok("Verification email sent", null);
    }

    @GetMapping("/confirm")
    public ApiResponse<DeviceResponse> confirmDevice(@RequestParam String token) {
        DeviceBinding device = deviceBindingRepository.findByVerificationToken(token)
            .orElseThrow(() -> RitSyncException.badRequest("Invalid or expired verification token"));
        
        if (device.getVerificationExpiry() == null || device.getVerificationExpiry().isBefore(LocalDateTime.now())) {
            throw RitSyncException.badRequest("Verification token has expired");
        }
        
        // Only one trusted device per user: confirming this one untrusts the others.
        deviceVerificationService.trustExclusively(device);

        log.info("Device {} verified for user {}", device.getId(), device.getUser().getId());
        
        return ApiResponse.ok("Device verified successfully", DeviceResponse.from(device));
    }

    @DeleteMapping("/{deviceId}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> revokeDevice(
            @PathVariable Long deviceId,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        DeviceBinding device = deviceBindingRepository.findById(deviceId)
            .orElseThrow(() -> RitSyncException.notFound("Device not found"));
        
        if (!device.getUser().getId().equals(principal.getId())) {
            throw RitSyncException.forbidden("This device does not belong to you");
        }
        
        device.setIsTrusted(false);
        deviceBindingRepository.save(device);
        
        return ApiResponse.ok("Device revoked", null);
    }

    public record DeviceResponse(
        Long id,
        String deviceName,
        String deviceFingerprint,
        Boolean isTrusted,
        LocalDateTime boundAt,
        LocalDateTime lastSeenAt,
        LocalDateTime verifiedAt
    ) {
        public static DeviceResponse from(DeviceBinding binding) {
            return new DeviceResponse(
                binding.getId(),
                binding.getDeviceName(),
                maskFingerprint(binding.getDeviceFingerprint()),
                binding.getIsTrusted(),
                binding.getBoundAt(),
                binding.getLastSeenAt(),
                binding.getVerifiedAt()
            );
        }
        
        private static String maskFingerprint(String fp) {
            if (fp == null || fp.length() < 8) return "****";
            return fp.substring(0, 4) + "****" + fp.substring(fp.length() - 4);
        }
    }
}
