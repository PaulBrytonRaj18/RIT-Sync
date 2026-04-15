package com.ritsync.repository;

import com.ritsync.entity.DeviceBinding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceBindingRepository extends JpaRepository<DeviceBinding, Long> {
    Optional<DeviceBinding> findByUserIdAndDeviceFingerprint(Long userId, String fingerprint);
    List<DeviceBinding> findByUserId(Long userId);
    boolean existsByUserIdAndDeviceFingerprintAndIsTrustedTrue(Long userId, String fingerprint);
}
