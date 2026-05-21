package com.ritsync.repository;

import com.ritsync.entity.Beacon;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface BeaconRepository extends JpaRepository<Beacon, Long> {
    Optional<Beacon> findByUuidAndIsActiveTrue(String uuid);
    List<Beacon> findBySessionIdAndIsActiveTrue(Long sessionId);
    void deleteBySessionId(Long sessionId);
}
