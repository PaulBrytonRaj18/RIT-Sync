package com.ritsync.service;

import com.ritsync.entity.DeviceBinding;
import com.ritsync.entity.User;
import com.ritsync.repository.DeviceBindingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceVerificationService {

    private final DeviceBindingRepository deviceBindingRepository;

    public record DeviceResult(boolean verified, boolean firstTime) {}

    @Transactional
    public DeviceResult verifyAndBind(User user, String fingerprint, String deviceName) {
        Optional<DeviceBinding> existing =
            deviceBindingRepository.findByUserIdAndDeviceFingerprint(user.getId(), fingerprint);

        if (existing.isPresent()) {
            DeviceBinding binding = existing.get();
            binding.setLastSeenAt(LocalDateTime.now());
            deviceBindingRepository.save(binding);

            if (!Boolean.TRUE.equals(binding.getIsTrusted())) {
                log.warn("Device {} for user {} is revoked", fingerprint, user.getEmail());
                return new DeviceResult(false, false);
            }
            return new DeviceResult(true, false);
        }

        DeviceBinding newBinding = DeviceBinding.builder()
            .user(user)
            .deviceFingerprint(fingerprint)
            .deviceName(deviceName != null ? deviceName : "Unknown device")
            .isTrusted(false)
            .lastSeenAt(LocalDateTime.now())
            .build();
        deviceBindingRepository.save(newBinding);
        log.info("New device bound for user {}: {}. Verification required.", user.getEmail(), deviceName);
        return new DeviceResult(true, true);
    }

    public boolean isTrusted(Long userId, String fingerprint) {
        return deviceBindingRepository
            .existsByUserIdAndDeviceFingerprintAndIsTrustedTrue(userId, fingerprint);
    }
}
