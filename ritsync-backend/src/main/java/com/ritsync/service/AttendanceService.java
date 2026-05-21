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
import com.ritsync.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceRecordRepository     attendanceRepo;
    private final SessionRepository             sessionRepo;
    private final UserService                  userService;
    private final FaceVerificationService       faceService;
    private final GeofenceService              geoService;
    private final DeviceVerificationService     deviceService;
    private final LivenessVerificationService   livenessService;
    private final BeaconVerificationService     beaconService;
    private final AuditService                 auditService;
    private final NotificationService          notificationService;

    @Transactional
    public MarkAttendanceResponse markAttendance(Long studentId, MarkAttendanceRequest req) {

        Session session = sessionRepo.findById(req.sessionId())
            .orElseThrow(() -> RitSyncException.notFound("Session not found: " + req.sessionId()));

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

        Optional<AttendanceRecord> existing =
            attendanceRepo.findBySessionIdAndStudentId(req.sessionId(), studentId);
        if (existing.isPresent()) {
            AttendanceRecord existingRecord = existing.get();
            if (existingRecord.getStatus() == AttendanceStatus.PRESENT) {
                throw RitSyncException.conflict("Attendance already marked as present for this session.");
            }
            if (existingRecord.getStatus() == AttendanceStatus.EXCUSED) {
                throw RitSyncException.conflict("Attendance already excused for this session.");
            }
            if (!existingRecord.getManuallyOverridden() && existingRecord.getMarkedAt() != null) {
                log.info("Student {} already has attendance record for session {} with status {}",
                    studentId, req.sessionId(), existingRecord.getStatus());
            }
        }

        FaceVerificationService.VerificationResult faceResult =
            faceService.verify(studentId, req.faceDescriptor());
        log.info("[FACE]   student={} distance={:.4f} verified={}",
                 studentId, faceResult.distance(), faceResult.verified());

        // Liveness verification (client-side result, server validates if provided)
        boolean livenessVerified = Boolean.TRUE.equals(req.livenessVerified());
        int blinkCount = req.blinkCount() != null ? req.blinkCount() : 0;
        log.info("[LIVENESS] student={} verified={} blinks={}", studentId, livenessVerified, blinkCount);

        double gpsDistance = geoService.distanceMetres(
            req.userLat(), req.userLng(),
            session.getGeofenceLat().doubleValue(),
            session.getGeofenceLng().doubleValue()
        );
        boolean gpsVerified = gpsDistance <= session.getGeofenceRadiusM();
        log.info("[GPS]    student={} distance={:.1f}m radius={}m verified={}",
                 studentId, gpsDistance, session.getGeofenceRadiusM(), gpsVerified);

        // BLE beacon verification
        boolean beaconVerified = false;
        if (req.beaconUuid() != null && req.beaconToken() != null) {
            beaconVerified = beaconService.validateBeacon(
                req.beaconUuid(), req.beaconToken(), req.beaconRssi()
            );
        } else {
            beaconVerified = true; // BLE not required, skip check
        }
        log.info("[BEACON] student={} verified={}", studentId, beaconVerified);

        DeviceVerificationService.DeviceResult deviceResult = deviceService.verifyAndBind(
            student, req.deviceFingerprint(), req.deviceName()
        );
        log.info("[DEVICE] student={} fingerprint={} trusted={}",
                 studentId, req.deviceFingerprint(), deviceResult.verified());

        boolean allVerified = faceResult.verified() && gpsVerified && deviceResult.verified()
                            && livenessVerified && beaconVerified;
        AttendanceStatus status = allVerified ? AttendanceStatus.PRESENT : AttendanceStatus.ABSENT;

        AttendanceRecord record = existing.orElseGet(() ->
            AttendanceRecord.builder()
                .session(session)
                .student(student)
                .build()
        );
        record.setStatus(status);
        record.setFaceVerified(faceResult.verified());
        record.setLivenessVerified(livenessVerified);
        record.setBlinkCount(blinkCount);
        record.setGpsVerified(gpsVerified);
        record.setGpsAccuracy(req.gpsAccuracy());
        record.setBeaconVerified(beaconVerified);
        record.setBeaconUuid(req.beaconUuid());
        record.setBeaconToken(req.beaconToken());
        record.setBeaconRssi(req.beaconRssi());
        record.setDeviceVerified(deviceResult.verified());
        record.setUserLat(req.userLat());
        record.setUserLng(req.userLng());
        record.setMarkedAt(LocalDateTime.now());
        attendanceRepo.save(record);

        auditService.log(studentId, student.getEmail(), "ATTENDANCE_MARKED",
            "attendance_records", record.getId(),
            Map.of(
                "sessionId",         req.sessionId(),
                "status",            status.name(),
                "faceOk",            faceResult.verified(),
                "livenessOk",        livenessVerified,
                "gpsOk",             gpsVerified,
                "beaconOk",          beaconVerified,
                "deviceOk",          deviceResult.verified(),
                "faceDistance",       faceResult.distance(),
                "gpsDistance",        gpsDistance,
                "blinkCount",        blinkCount
            ), null);

        notificationService.checkAndNotifyThreshold(studentId, session.getCourse().getId());

        String message = buildMessage(faceResult.verified(), livenessVerified, gpsVerified, beaconVerified, deviceResult.verified(), allVerified, session);

        return new MarkAttendanceResponse(
            record.getId(), status,
            faceResult.verified(), gpsVerified, deviceResult.verified(),
            livenessVerified, beaconVerified,
            allVerified, message,
            faceResult.distance(), gpsDistance
        );
    }

    @Transactional(readOnly = true)
    public List<AttendanceRecordResponse> getSessionAttendance(Long sessionId) {
        return attendanceRepo.findBySessionId(sessionId)
            .stream().map(AttendanceRecordResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<AttendanceRecordResponse> getMyRecords(Long studentId) {
        return attendanceRepo.findByStudentId(studentId)
            .stream().map(AttendanceRecordResponse::from).toList();
    }

    public AttendanceStatsResponse getStudentStats(Long studentId, Long courseId) {
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
                                                  ManualOverrideRequest req, Long actorId) {
        AttendanceRecord record = attendanceRepo.findBySessionIdAndStudentId(sessionId, studentId)
            .orElseThrow(() -> RitSyncException.notFound("Attendance record not found"));

        User actor = userService.findOrThrow(actorId);
        record.setStatus(req.status());
        record.setManuallyOverridden(true);
        record.setOverrideReason(req.reason());
        record.setOverriddenBy(actor);
        attendanceRepo.save(record);

        auditService.log(actorId, actor.getEmail(), "MANUAL_OVERRIDE",
            "attendance_records", record.getId(),
            Map.of("studentId", studentId, "newStatus", req.status().name(), "reason", req.reason()),
            null);

        return AttendanceRecordResponse.from(record);
    }

    private String buildMessage(boolean face, boolean liveness, boolean gps, boolean beacon, boolean device, boolean all, Session session) {
        if (all) return "Attendance marked successfully. All verifications passed.";
        StringBuilder sb = new StringBuilder("Attendance could not be marked. ");
        
        if (!face)    sb.append("Face ID verification failed - ensure you are looking directly at the camera. ");
        if (!liveness)sb.append("Liveness check failed - please blink naturally during verification. ");
        if (!gps)     sb.append(String.format("GPS location outside the %dm classroom radius. ", session.getGeofenceRadiusM()));
        if (!beacon)  sb.append("Bluetooth beacon verification failed - ensure you are in the classroom. ");
        if (!device)  sb.append("This device is not registered for attendance. Please use a registered device. ");
        
        sb.append("Contact your teacher if you believe this is an error.");
        return sb.toString().trim();
    }
}
