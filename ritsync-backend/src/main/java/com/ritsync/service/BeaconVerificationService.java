package com.ritsync.service;

import com.ritsync.entity.Beacon;
import com.ritsync.entity.Session;
import com.ritsync.repository.BeaconRepository;
import com.ritsync.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BeaconVerificationService {

    private final BeaconRepository beaconRepository;
    private final SessionRepository sessionRepository;

    @Value("${ritsync.beacon.token-expiry-seconds:60}")
    private int tokenExpirySeconds;

    private static final int DEFAULT_RSSI_THRESHOLD = -75;

    public Beacon generateOrUpdateBeacon(Long sessionId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        List<Beacon> existing = beaconRepository.findBySessionIdAndIsActiveTrue(sessionId);
        Beacon beacon = existing.isEmpty() ? new Beacon() : existing.get(0);

        beacon.setSession(session);
        beacon.setUuid(existing.isEmpty() ? UUID.randomUUID().toString() : beacon.getUuid());
        beacon.setToken(UUID.randomUUID().toString());
        beacon.setTokenExpiry(LocalDateTime.now().plusSeconds(tokenExpirySeconds));
        beacon.setRssiThreshold(DEFAULT_RSSI_THRESHOLD);
        beacon.setActive(true);

        return beaconRepository.save(beacon);
    }

    public Optional<Beacon> getBeaconForSession(Long sessionId) {
        return beaconRepository.findBySessionIdAndIsActiveTrue(sessionId).stream().findFirst();
    }

    public boolean validateBeacon(String beaconUuid, String beaconToken, Integer rssi) {
        return beaconRepository.findByUuidAndIsActiveTrue(beaconUuid)
                .map(beacon -> {
                    boolean tokenValid = beacon.getTokenExpiry().isAfter(LocalDateTime.now());
                    boolean tokenMatch = beacon.getToken().equals(beaconToken);
                    boolean rssiValid = rssi != null && rssi >= beacon.getRssiThreshold();

                    log.info("Beacon check: uuid={}, tokenValid={}, rssiValid={}",
                            beaconUuid, tokenValid, rssiValid);
                    return tokenValid && tokenMatch && rssiValid;
                })
                .orElse(false);
    }

    @Scheduled(fixedDelay = 60000)
    public void expireOldBeacons() {
        List<Beacon> stale = beaconRepository.findAll().stream()
                .filter(b -> b.isActive() && b.getTokenExpiry().isBefore(LocalDateTime.now()))
                .toList();
        stale.forEach(b -> b.setActive(false));
        beaconRepository.saveAll(stale);
        log.info("Expired {} stale beacons", stale.size());
    }
}
