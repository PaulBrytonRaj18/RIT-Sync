package com.ritsync.service;

import com.ritsync.dto.request.ManualOverrideRequest;
import com.ritsync.dto.request.MarkAttendanceRequest;
import com.ritsync.dto.response.AttendanceRecordResponse;
import com.ritsync.dto.response.AttendanceStatsResponse;
import com.ritsync.dto.response.MarkAttendanceResponse;
import com.ritsync.entity.AttendanceRecord;
import com.ritsync.entity.Session;
import com.ritsync.entity.User;
import com.ritsync.enums.AttendanceStatus;
import com.ritsync.exception.RitSyncException;
import com.ritsync.repository.AttendanceRecordRepository;
import com.ritsync.security.AccessGuard;
import com.ritsync.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Attendance marking. Every decision is made on the server from the evidence submitted;
 * no client-reported "verified" flag is trusted.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceRecordRepository  attendanceRepo;
    private final UserService                 userService;
    private final FaceVerificationService     faceService;
    private final GeofenceService             geoService;
    private final DeviceVerificationService   deviceService;
    private final LivenessVerificationService livenessService;
    private final BeaconVerificationService   beaconService;
    private final AuditService                auditService;
    private final NotificationService         notificationService;
    private final AccessGuard                 guard;

    @Value("${app.attendance.max-attempts:5}")
    private int maxAttempts;

    /** Reported GPS accuracy worse than this (metres) cannot pass the geofence check. */
    @Value("${app.geofence.max-accuracy-m:150}")
    private double maxGpsAccuracyM;

    @Transactional
    public MarkAttendanceResponse markAttendance(Long studentId, MarkAttendanceRequest req) {
        Session session = guard.session(req.sessionId());
        Long courseId = session.getCourse().getId();

        // 1. Eligibility ─────────────────────────────────────────────────────────
        if (!guard.isEnrolled(studentId, courseId)) {
            throw RitSyncException.forbidden("You are not enrolled in this course.");
        }
        if (!Boolean.TRUE.equals(session.getIsOpen())) {
            throw RitSyncException.badRequest("Session is not open for attendance.");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(session.getStartTime())) {
            throw RitSyncException.badRequest("Session has not started yet. Please wait until the session begins.");
        }
        if (now.isAfter(session.getEndTime())) {
            throw RitSyncException.badRequest("Session has ended. Attendance can no longer be marked.");
        }

        User student = userService.findOrThrow(studentId);
        AttendanceRecord record = attendanceRepo.findBySessionIdAndStudentId(session.getId(), studentId)
            .orElseGet(() -> AttendanceRecord.builder().session(session).student(student).build());

        if (record.getStatus() == AttendanceStatus.PRESENT || record.getStatus() == AttendanceStatus.LATE) {
            throw RitSyncException.conflict("Attendance already marked for this session.");
        }
        if (record.getStatus() == AttendanceStatus.EXCUSED) {
            throw RitSyncException.conflict("Attendance already excused for this session.");
        }
        int attempts = record.getAttemptCount() == null ? 0 : record.getAttemptCount();
        if (attempts >= maxAttempts) {
            throw RitSyncException.tooManyRequests(
                "Maximum verification attempts reached for this session. Please contact your teacher.");
        }
        record.setAttemptCount(attempts + 1);

        // 2. Face ────────────────────────────────────────────────────────────────
        String descriptorHash = hashDescriptor(req.faceDescriptor());
        boolean reusedDescriptor = attendanceRepo.existsByStudentIdAndFaceDescriptorHash(studentId, descriptorHash);
        FaceVerificationService.VerificationResult face = faceService.verify(studentId, req.faceDescriptor());
        boolean faceOk = face.verified() && !reusedDescriptor;
        if (reusedDescriptor) {
            log.warn("Student {} re-submitted an identical face descriptor (replay)", studentId);
        }

        // 3. Liveness (only when the session requires it; evaluated server-side) ─
        boolean livenessRequired = Boolean.TRUE.equals(session.getEnableLiveness());
        LivenessVerificationService.LivenessResult liveness = livenessRequired
            ? livenessService.validate(req.livenessFrames())
            : null;
        boolean livenessOk = !livenessRequired || liveness.success();

        // 4. GPS geofence ────────────────────────────────────────────────────────
        double gpsDistance = geoService.distanceMetres(
            req.userLat(), req.userLng(),
            session.getGeofenceLat().doubleValue(), session.getGeofenceLng().doubleValue());
        boolean accuracyOk = req.gpsAccuracy() != null && req.gpsAccuracy() <= maxGpsAccuracyM;
        boolean gpsOk = accuracyOk && gpsDistance <= session.getGeofenceRadiusM();

        // 5. BLE beacon (only when required; must belong to THIS session) ───────
        boolean beaconRequired = Boolean.TRUE.equals(session.getEnableBle());
        boolean beaconOk = !beaconRequired || beaconService.validateBeacon(
            session.getId(), req.beaconUuid(), req.beaconToken(), req.beaconRssi());

        // 6. Device ──────────────────────────────────────────────────────────────
        boolean deviceOk = deviceService.verifyAndBind(student, req.deviceFingerprint(), req.deviceName()).verified();

        boolean allVerified = faceOk && livenessOk && gpsOk && beaconOk && deviceOk;
        if (allVerified) {
            record.setStatus(AttendanceStatus.PRESENT);
            record.setMarkedAt(now);
        }
        // A failed attempt leaves the status unchanged (ABSENT) but keeps the evidence for review.
        record.setFaceVerified(faceOk);
        record.setFaceDescriptorHash(descriptorHash);
        record.setLivenessVerified(liveness != null && liveness.success());
        record.setBlinkCount(liveness != null ? liveness.blinkCount() : 0);
        record.setGpsVerified(gpsOk);
        record.setGpsAccuracy(req.gpsAccuracy());
        record.setUserLat(req.userLat());
        record.setUserLng(req.userLng());
        record.setBeaconVerified(beaconRequired && beaconOk);
        record.setBeaconUuid(req.beaconUuid());
        record.setBeaconRssi(req.beaconRssi());
        record.setBeaconToken(null); // never persist bearer-like tokens
        record.setDeviceVerified(deviceOk);
        attendanceRepo.save(record);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("sessionId", session.getId());
        audit.put("attempt", record.getAttemptCount());
        audit.put("status", record.getStatus().name());
        audit.put("faceOk", faceOk);
        audit.put("faceDistance", face.distance());
        audit.put("faceReplay", face.suspectedReplay() || reusedDescriptor);
        audit.put("livenessRequired", livenessRequired);
        audit.put("livenessOk", livenessOk);
        audit.put("gpsOk", gpsOk);
        audit.put("gpsDistance", gpsDistance);
        audit.put("gpsAccuracy", req.gpsAccuracy());
        audit.put("beaconRequired", beaconRequired);
        audit.put("beaconOk", beaconOk);
        audit.put("deviceOk", deviceOk);
        auditService.log(studentId, student.getEmail(), "ATTENDANCE_ATTEMPT",
            "attendance_records", record.getId(), audit, null);

        if (allVerified) {
            notificationService.checkAndNotifyThreshold(studentId, courseId);
        }

        String message = buildMessage(faceOk, face.suspectedReplay() || reusedDescriptor, livenessOk, gpsOk,
            accuracyOk, beaconOk, deviceOk, allVerified, session);

        return new MarkAttendanceResponse(
            record.getId(), record.getStatus(),
            faceOk, gpsOk, deviceOk, livenessOk, beaconOk, allVerified, message,
            face.distance(), gpsDistance,
            livenessRequired, beaconRequired,
            Math.max(0, maxAttempts - record.getAttemptCount()));
    }

    @Transactional(readOnly = true)
    public List<AttendanceRecordResponse> getSessionAttendance(Long sessionId, UserPrincipal actor) {
        guard.requireManageSession(sessionId, actor);
        return attendanceRepo.findBySessionId(sessionId)
            .stream().map(AttendanceRecordResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<AttendanceRecordResponse> getMyRecords(Long studentId) {
        return attendanceRepo.findByStudentId(studentId)
            .stream().map(AttendanceRecordResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public AttendanceStatsResponse getStudentStats(Long studentId, Long courseId, UserPrincipal actor) {
        if (AccessGuard.isStudent(actor)) {
            if (!actor.getId().equals(studentId)) {
                throw RitSyncException.forbidden("You can only view your own attendance.");
            }
        } else {
            guard.requireManage(courseId, actor);
        }

        long total   = attendanceRepo.countTotalSessionsByStudentAndCourse(studentId, courseId);
        long present = attendanceRepo.countByStudentAndCourseAndStatus(studentId, courseId, AttendanceStatus.PRESENT);
        long absent  = attendanceRepo.countByStudentAndCourseAndStatus(studentId, courseId, AttendanceStatus.ABSENT);
        long late    = attendanceRepo.countByStudentAndCourseAndStatus(studentId, courseId, AttendanceStatus.LATE);
        long excused = attendanceRepo.countByStudentAndCourseAndStatus(studentId, courseId, AttendanceStatus.EXCUSED);
        double pct   = total == 0 ? 0.0 : (present + late) * 100.0 / total;

        return new AttendanceStatsResponse(
            studentId, courseId, total, present, absent, late, excused, pct,
            Map.of("PRESENT", present, "ABSENT", absent, "LATE", late, "EXCUSED", excused)
        );
    }

    @Transactional
    public AttendanceRecordResponse manualOverride(Long sessionId, Long studentId,
                                                   ManualOverrideRequest req, UserPrincipal actorPrincipal) {
        guard.requireManageSession(sessionId, actorPrincipal);
        AttendanceRecord record = attendanceRepo.findBySessionIdAndStudentId(sessionId, studentId)
            .orElseThrow(() -> RitSyncException.notFound("Attendance record not found"));

        User actor = userService.findOrThrow(actorPrincipal.getId());
        AttendanceStatus previous = record.getStatus();
        record.setStatus(req.status());
        record.setManuallyOverridden(true);
        record.setOverrideReason(req.reason());
        record.setOverriddenBy(actor);
        attendanceRepo.save(record);

        auditService.log(actor.getId(), actor.getEmail(), "MANUAL_OVERRIDE",
            "attendance_records", record.getId(),
            Map.of("studentId", studentId, "previousStatus", previous.name(),
                   "newStatus", req.status().name(), "reason", req.reason()),
            null);

        return AttendanceRecordResponse.from(record);
    }

    private static String hashDescriptor(List<Double> descriptor) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            for (Double d : descriptor) {
                sha.update(Double.toString(d).getBytes(StandardCharsets.UTF_8));
                sha.update((byte) ',');
            }
            return HexFormat.of().formatHex(sha.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String buildMessage(boolean face, boolean replay, boolean liveness, boolean gps,
                                       boolean accuracyOk, boolean beacon, boolean device,
                                       boolean all, Session session) {
        if (all) return "Attendance marked successfully. All verifications passed.";
        StringBuilder sb = new StringBuilder("Attendance could not be marked. ");
        if (replay)        sb.append("The face capture looks reused - please capture a fresh image. ");
        else if (!face)    sb.append("Face ID verification failed - ensure you are looking directly at the camera. ");
        if (!liveness)     sb.append("Liveness check failed - please blink naturally during verification. ");
        if (!accuracyOk)   sb.append("Your GPS signal is too imprecise - move near a window or enable precise location. ");
        else if (!gps)     sb.append(String.format("GPS location outside the %dm classroom radius. ", session.getGeofenceRadiusM()));
        if (!beacon)       sb.append("Bluetooth beacon verification failed - ensure you are in the classroom. ");
        if (!device)       sb.append("This device is not trusted yet. Verify it from Devices, or use your registered device. ");
        sb.append("Contact your teacher if you believe this is an error.");
        return sb.toString().trim();
    }
}
