package com.ritsync.service;

import com.ritsync.dto.request.LoginRequest;
import com.ritsync.dto.request.RegisterRequest;
import com.ritsync.dto.response.AuthResponse;
import com.ritsync.dto.response.UserResponse;
import com.ritsync.entity.DeviceBinding;
import com.ritsync.entity.User;
import com.ritsync.enums.Role;
import com.ritsync.exception.RitSyncException;
import com.ritsync.repository.DeviceBindingRepository;
import com.ritsync.repository.UserRepository;
import com.ritsync.security.JwtTokenProvider;
import com.ritsync.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository        userRepository;
    private final DeviceBindingRepository deviceBindingRepo;
    private final PasswordEncoder       passwordEncoder;
    private final JwtTokenProvider      tokenProvider;
    private final AuthenticationManager authManager;
    private final AuditService          auditService;
    private final DeviceVerificationService deviceService;

    @Value("${jwt.access-token-expiry-ms}")
    private long accessExpiry;

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            throw RitSyncException.conflict("Email already registered: " + req.email());
        }
        // Self-registration is for students only. Teacher and admin accounts are created by
        // an administrator (POST /admin/users), otherwise anyone could grant themselves staff rights.
        if (req.role() != null && req.role() != Role.STUDENT) {
            throw RitSyncException.forbidden(
                "Only students can self-register. Teacher accounts are created by an administrator.");
        }
        if (req.rollNumber() == null || req.rollNumber().isBlank()) {
            throw RitSyncException.badRequest("Roll number is required.");
        }

        User user = User.builder()
            .name(req.name())
            .email(req.email())
            .passwordHash(passwordEncoder.encode(req.password()))
            .role(Role.STUDENT)
            .department(req.department())
            .rollNumber(req.rollNumber())
            .phone(req.phone())
            .build();

        user = userRepository.save(user);

        if (req.deviceFingerprint() != null && !req.deviceFingerprint().isBlank()) {
            bindDevice(user, req.deviceFingerprint(), req.deviceName());
        }

        auditService.log(user.getId(), user.getEmail(), "USER_REGISTERED", "users", user.getId(), null, null);

        var principal = new UserPrincipal(user);
        String access  = tokenProvider.generateAccessToken(principal);
        String refresh = tokenProvider.generateRefreshToken(principal);

        return AuthResponse.of(access, refresh, accessExpiry / 1000, UserResponse.from(user, false));
    }

    @Transactional
    public AuthResponse login(LoginRequest req) {
        Authentication auth = authManager.authenticate(
            new UsernamePasswordAuthenticationToken(req.email(), req.password())
        );

        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        User user = userRepository.findById(principal.getId()).orElseThrow();

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw RitSyncException.unauthorized("Account is disabled. Please contact administrator.");
        }

        // Email verification check (skip for now - requires email setup)
        // if (!Boolean.TRUE.equals(user.getIsEmailVerified())) {
        //     throw RitSyncException.unauthorized("Please verify your email before logging in.");
        // }

        log.info("User {} logged in", user.getId());

        bindDevice(user, req.deviceFingerprint(), req.deviceName());

        auditService.log(user.getId(), user.getEmail(), "USER_LOGIN", "users", user.getId(), null, null);

        String access  = tokenProvider.generateAccessToken(principal);
        String refresh = tokenProvider.generateRefreshToken(principal);
        boolean faceEnrolled = userRepository.existsFaceDescriptorByUserId(user.getId());

        return AuthResponse.of(access, refresh, accessExpiry / 1000, UserResponse.from(user, faceEnrolled));
    }

    private void bindDevice(User user, String fingerprint, String deviceName) {
        if (fingerprint == null || fingerprint.isBlank()) {
            return;
        }
        deviceService.recordDevice(user, fingerprint, deviceName);
    }

    public boolean isDeviceTrusted(Long userId, String fingerprint) {
        return deviceBindingRepo.existsByUserIdAndDeviceFingerprintAndIsTrustedTrue(userId, fingerprint);
    }

    /**
     * Exchanges a valid refresh token for a new access + refresh token pair.
     * The account is re-checked so disabled/deleted users cannot keep refreshing.
     */
    @Transactional(readOnly = true)
    public AuthResponse refresh(String refreshToken) {
        String email = tokenProvider.validateRefreshToken(refreshToken)
            .orElseThrow(() -> RitSyncException.unauthorized("Invalid or expired refresh token"));

        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> RitSyncException.unauthorized("Invalid or expired refresh token"));
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw RitSyncException.unauthorized("Account is disabled. Please contact administrator.");
        }

        var principal = new UserPrincipal(user);
        boolean faceEnrolled = userRepository.existsFaceDescriptorByUserId(user.getId());
        return AuthResponse.of(
            tokenProvider.generateAccessToken(principal),
            tokenProvider.generateRefreshToken(principal),
            accessExpiry / 1000,
            UserResponse.from(user, faceEnrolled));
    }
}
