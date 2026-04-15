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

    private final AttendanceRecordRepository  attendanceRepo;
    private final SessionRepository          sessionRepo;
    private final UserService               userService;
    private final FaceVerificationService    faceService;
    private final GeofenceService           geoService;
    private final DeviceVerificationService  deviceService;
    private final AuditService              auditService;
    private final NotificationService       notificationService;

    @Transactional
    public MarkAttendanceResponse markAttendance(Long studentId, MarkAttendanceRequest req) {

        Session session = sessionRepo.findById(req.sessionId())
            .orElseThrow(() -> RitSyncException.notFound("Session not found: " + req.sessionId()));

        if (!Boolean.TRUE.equals(session.getIsOpen())) {
            throw RitSyncException.badRequest("Session is not open for attendance.");
        }

        User student = userService.findOrThrow(studentId);

        Optional<AttendanceRecord> existing =
            attendanceRepo.findBySessionIdAndStudentId(req.sessionId(), studentId);
        if (existing.isPresent() && existing.get().getStatus() == AttendanceStatus.PRESENT) {
            throw RitSyncException.conflict("Attendance already marked for this session.");
        }

        FaceVerificationService.VerificationResult faceResult =
            faceService.verify(studentId, req.faceDescriptor());
        log.info("[FACE]   student={} distance={:.4f} verified={}",
                 studentId, faceResult.distance(), faceResult.verified());

        double gpsDistance = geoService.distanceMetres(
            req.userLat(), req.userLng(),
            session.getGeofenceLat().doubleValue(),
            session.getGeofenceLng().doubleValue()
        );
        boolean gpsVerified = gpsDistance <= session.getGeofenceRadiusM();
        log.info("[GPS]    student={} distance={:.1f}m radius={}m verified={}",
                 studentId, gpsDistance, session.getGeofenceRadiusM(), gpsVerified);

        DeviceVerificationService.DeviceResult deviceResult = deviceService.verifyAndBind(
            student, req.deviceFingerprint(), req.deviceName()
        );
        log.info("[DEVICE] student={} fingerprint={} trusted={}",
                 studentId, req.deviceFingerprint(), deviceResult.verified());

        boolean allVerified = faceResult.verified() && gpsVerified && deviceResult.verified();
        AttendanceStatus status = allVerified ? AttendanceStatus.PRESENT : AttendanceStatus.ABSENT;

        AttendanceRecord record = existing.orElseGet(() ->
            AttendanceRecord.builder()
                .session(session)
                .student(student)
                .build()
        );
        record.setStatus(status);
        record.setFaceVerified(faceResult.verified());
        record.setGpsVerified(gpsVerified);
        record.setDeviceVerified(deviceResult.verified());
        record.setMarkedAt(LocalDateTime.now());
        attendanceRepo.save(record);

        auditService.log(studentId, student.getEmail(), "ATTENDANCE_MARKED",
            "attendance_records", record.getId(),
            Map.of(
                "sessionId",    req.sessionId(),
                "status",       status.name(),
                "faceOk",       faceResult.verified(),
                "gpsOk",        gpsVerified,
                "deviceOk",     deviceResult.verified(),
                "faceDistance",  faceResult.distance(),
                "gpsDistance",   gpsDistance
            ), null);

        notificationService.checkAndNotifyThreshold(studentId, session.getCourse().getId());

        String message = buildMessage(faceResult.verified(), gpsVerified, deviceResult.verified(), allVerified);

        return new MarkAttendanceResponse(
            record.getId(), status,
            faceResult.verified(), gpsVerified, deviceResult.verified(),
            allVerified, message,
            faceResult.distance(), gpsDistance
        );
    }

    public List<AttendanceRecordResponse> getSessionAttendance(Long sessionId) {
        return attendanceRepo.findBySessionId(sessionId)
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

    private String buildMessage(boolean face, boolean gps, boolean device, boolean all) {
        if (all) return "Attendance marked successfully. All three verifications passed.";
        StringBuilder sb = new StringBuilder("Attendance could not be marked. Failed: ");
        if (!face)   sb.append("Face ID (look directly at camera). ");
        if (!gps)    sb.append("GPS location (you appear to be outside the classroom). ");
        if (!device) sb.append("Device not trusted (use your registered device). ");
        return sb.toString().trim();
    }
}
