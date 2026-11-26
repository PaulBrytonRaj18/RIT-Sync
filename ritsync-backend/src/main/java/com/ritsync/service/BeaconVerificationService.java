package com.ritsync.service;

import com.ritsync.entity.Beacon;
import com.ritsync.entity.Session;
import com.ritsync.repository.BeaconRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BeaconVerificationService {

    private static final int DEFAULT_RSSI_THRESHOLD = -75;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final BeaconRepository beaconRepository;

    @Value("${ritsync.beacon.token-expiry-seconds:60}")
    private int tokenExpirySeconds;

    /** Creates the session's beacon, or rotates its token. Caller must already be authorised. */
    @Transactional
    public Beacon generateOrUpdateBeacon(Session session) {
        List<Beacon> existing = beaconRepository.findBySessionIdAndIsActiveTrue(session.getId());
        Beacon beacon = existing.isEmpty() ? new Beacon() : existing.get(0);

        beacon.setSession(session);
        if (beacon.getUuid() == null) {
            beacon.setUuid(UUID.randomUUID().toString());
        }
        beacon.setToken(newToken());
        beacon.setTokenExpiry(LocalDateTime.now().plusSeconds(tokenExpirySeconds));
        beacon.setRssiThreshold(DEFAULT_RSSI_THRESHOLD);
        beacon.setActive(true);
        return beaconRepository.save(beacon);
    }

    public Optional<Beacon> getBeaconForSession(Long sessionId) {
        return beaconRepository.findBySessionIdAndIsActiveTrue(sessionId).stream().findFirst();
    }

    /**
     * A beacon reading is valid only if it belongs to THIS session, its rotating token
     * matches and is unexpired, and the reported signal strength is strong enough.
     */
    @Transactional(readOnly = true)
    public boolean validateBeacon(Long sessionId, String beaconUuid, String beaconToken, Integer rssi) {
        if (beaconUuid == null || beaconToken == null) return false;
        return beaconRepository.findByUuidAndIsActiveTrue(beaconUuid)
            .filter(b -> b.getSession().getId().equals(sessionId))
            .map(b -> {
                boolean tokenValid = b.getTokenExpiry().isAfter(LocalDateTime.now());
                boolean tokenMatch = constantTimeEquals(b.getToken(), beaconToken);
                boolean rssiValid  = rssi != null && rssi >= b.getRssiThreshold();
                log.debug("Beacon check session={} tokenValid={} tokenMatch={} rssiValid={}",
                    sessionId, tokenValid, tokenMatch, rssiValid);
                return tokenValid && tokenMatch && rssiValid;
            })
            .orElse(false);
    }

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void expireOldBeacons() {
        List<Beacon> stale = beaconRepository.findByIsActiveTrueAndTokenExpiryBefore(LocalDateTime.now());
        if (stale.isEmpty()) return;
        stale.forEach(b -> b.setActive(false));
        beaconRepository.saveAll(stale);
        log.info("Expired {} stale beacons", stale.size());
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
