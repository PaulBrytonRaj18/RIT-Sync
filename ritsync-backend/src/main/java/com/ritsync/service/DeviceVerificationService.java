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

/**
 * Device binding policy:
 * <ul>
 *   <li><b>Trust on first use:</b> the very first device a user ever signs in from is trusted.</li>
 *   <li>Every later device is recorded as <b>untrusted</b> and must be confirmed via the emailed
 *       link before it can mark attendance.</li>
 *   <li>Only <b>one</b> device is trusted at a time: confirming a new device untrusts the others,
 *       so a student cannot keep a friend's phone trusted alongside their own.</li>
 *   <li>An admin can reset a user's devices, which re-arms trust on first use.</li>
 * </ul>
 * Note: the fingerprint is computed by the browser, so this deters casual proxying rather than
 * proving device identity.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceVerificationService {

    private final DeviceBindingRepository deviceBindingRepository;

    public record DeviceResult(boolean verified, boolean firstTime) {}

    /** Records the device on login/registration (trusting it only if it is the user's first). */
    @Transactional
    public DeviceBinding recordDevice(User user, String fingerprint, String deviceName) {
        Optional<DeviceBinding> existing =
            deviceBindingRepository.findByUserIdAndDeviceFingerprint(user.getId(), fingerprint);
        if (existing.isPresent()) {
            DeviceBinding binding = existing.get();
            binding.setLastSeenAt(LocalDateTime.now());
            return deviceBindingRepository.save(binding);
        }

        boolean firstEver = !deviceBindingRepository.existsByUserId(user.getId());
        DeviceBinding binding = DeviceBinding.builder()
            .user(user)
            .deviceFingerprint(fingerprint)
            .deviceName(deviceName != null && !deviceName.isBlank() ? deviceName : "Unknown device")
            .isTrusted(firstEver)
            .verifiedAt(firstEver ? LocalDateTime.now() : null)
            .lastSeenAt(LocalDateTime.now())
            .build();
        log.info("Device recorded for user {} (trusted={}, firstEver={})", user.getId(), firstEver, firstEver);
        return deviceBindingRepository.save(binding);
    }

    /** Used when marking attendance: only a trusted device passes. */
    @Transactional
    public DeviceResult verifyAndBind(User user, String fingerprint, String deviceName) {
        boolean known = deviceBindingRepository.findByUserIdAndDeviceFingerprint(user.getId(), fingerprint).isPresent();
        DeviceBinding binding = recordDevice(user, fingerprint, deviceName);
        boolean trusted = Boolean.TRUE.equals(binding.getIsTrusted());
        if (!trusted) {
            log.warn("Attendance attempt from untrusted device for user {}", user.getId());
        }
        return new DeviceResult(trusted, !known);
    }

    /** Trusts the given binding and untrusts every other device of that user. */
    @Transactional
    public void trustExclusively(DeviceBinding binding) {
        deviceBindingRepository.findByUserIdAndIsTrustedTrue(binding.getUser().getId()).stream()
            .filter(other -> !other.getId().equals(binding.getId()))
            .forEach(other -> {
                other.setIsTrusted(false);
                deviceBindingRepository.save(other);
            });
        binding.setIsTrusted(true);
        binding.setVerifiedAt(LocalDateTime.now());
        binding.setVerificationToken(null);
        binding.setVerificationExpiry(null);
        deviceBindingRepository.save(binding);
    }

    @Transactional
    public void resetDevices(Long userId) {
        deviceBindingRepository.deleteByUserId(userId);
    }

    public boolean isTrusted(Long userId, String fingerprint) {
        return deviceBindingRepository.existsByUserIdAndDeviceFingerprintAndIsTrustedTrue(userId, fingerprint);
    }
}
