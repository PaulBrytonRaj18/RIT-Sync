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

    @Value("${jwt.access-token-expiry-ms}")
    private long accessExpiry;

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            throw RitSyncException.conflict("Email already registered: " + req.email());
        }
        if (req.role() == Role.ADMIN) {
            throw RitSyncException.forbidden("Cannot self-register as ADMIN");
        }

        User user = User.builder()
            .name(req.name())
            .email(req.email())
            .passwordHash(passwordEncoder.encode(req.password()))
            .role(req.role())
            .department(req.department())
            .rollNumber(req.rollNumber())
            .employeeId(req.employeeId())
            .phone(req.phone())
            .build();

        user = userRepository.save(user);
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

        // Update or create device binding
        bindDevice(user, req.deviceFingerprint(), req.deviceName());

        auditService.log(user.getId(), user.getEmail(), "USER_LOGIN", "users", user.getId(), null, null);

        String access  = tokenProvider.generateAccessToken(principal);
        String refresh = tokenProvider.generateRefreshToken(principal);
        boolean faceEnrolled = userRepository.existsFaceDescriptorByUserId(user.getId());

        return AuthResponse.of(access, refresh, accessExpiry / 1000, UserResponse.from(user, faceEnrolled));
    }

    private void bindDevice(User user, String fingerprint, String deviceName) {
        var existing = deviceBindingRepo.findByUserIdAndDeviceFingerprint(user.getId(), fingerprint);
        if (existing.isPresent()) {
            existing.get().setLastSeenAt(LocalDateTime.now());
            deviceBindingRepo.save(existing.get());
        } else {
            var binding = DeviceBinding.builder()
                .user(user)
                .deviceFingerprint(fingerprint)
                .deviceName(deviceName)
                .isTrusted(true)
                .build();
            deviceBindingRepo.save(binding);
            log.info("New device bound for user {}: {}", user.getEmail(), deviceName);
        }
    }

    public boolean isDeviceTrusted(Long userId, String fingerprint) {
        return deviceBindingRepo.existsByUserIdAndDeviceFingerprintAndIsTrustedTrue(userId, fingerprint);
    }
}
