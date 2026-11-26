package com.ritsync.service;

import com.ritsync.dto.request.CreateUserRequest;
import com.ritsync.dto.response.UserResponse;
import com.ritsync.entity.User;
import com.ritsync.enums.Role;
import com.ritsync.exception.RitSyncException;
import com.ritsync.repository.UserRepository;
import com.ritsync.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository            userRepository;
    private final PasswordEncoder           passwordEncoder;
    private final FaceDescriptorService     faceDescriptorService;
    private final DeviceVerificationService deviceService;
    private final AuditService              auditService;

    @Transactional
    public UserResponse createUser(CreateUserRequest req, UserPrincipal admin) {
        if (userRepository.existsByEmail(req.email())) {
            throw RitSyncException.conflict("Email already registered: " + req.email());
        }
        if (req.role() == Role.STUDENT && isBlank(req.rollNumber())) {
            throw RitSyncException.badRequest("Roll number is required for students.");
        }
        if (req.role() == Role.TEACHER && isBlank(req.employeeId())) {
            throw RitSyncException.badRequest("Employee ID is required for teachers.");
        }

        User user = userRepository.save(User.builder()
            .name(req.name())
            .email(req.email())
            .passwordHash(passwordEncoder.encode(req.password()))
            .role(req.role())
            .department(req.department())
            .rollNumber(req.role() == Role.STUDENT ? req.rollNumber() : null)
            .employeeId(req.role() != Role.STUDENT ? req.employeeId() : null)
            .phone(req.phone())
            .build());

        auditService.log(admin.getId(), admin.getEmail(), "USER_CREATED", "users", user.getId(),
            java.util.Map.of("role", user.getRole().name()), null);
        return UserResponse.from(user, false);
    }

    @Transactional
    public void resetFace(Long userId, UserPrincipal admin) {
        requireUser(userId);
        faceDescriptorService.resetEnrollment(userId);
        auditService.log(admin.getId(), admin.getEmail(), "FACE_RESET", "users", userId, null, null);
    }

    @Transactional
    public void resetDevices(Long userId, UserPrincipal admin) {
        requireUser(userId);
        deviceService.resetDevices(userId);
        auditService.log(admin.getId(), admin.getEmail(), "DEVICES_RESET", "users", userId, null, null);
    }

    private void requireUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw RitSyncException.notFound("User not found: " + userId);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
