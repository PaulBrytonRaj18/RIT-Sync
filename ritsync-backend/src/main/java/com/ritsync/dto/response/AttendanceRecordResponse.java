package com.ritsync.dto.response;

import com.ritsync.entity.AttendanceRecord;
import com.ritsync.enums.AttendanceStatus;
import java.time.LocalDateTime;

public record AttendanceRecordResponse(
    Long             id,
    Long             sessionId,
    Long             studentId,
    Long             courseId,
    String           studentName,
    String           studentRollNumber,
    AttendanceStatus status,
    boolean          faceVerified,
    boolean          gpsVerified,
    boolean          deviceVerified,
    boolean          livenessVerified,
    boolean          beaconVerified,
    boolean          manuallyOverridden,
    String           overrideReason,
    LocalDateTime    markedAt,
    String           courseName,
    String           courseCode,
    String           sessionTitle,
    String           verificationMethod
) {
    public static AttendanceRecordResponse from(AttendanceRecord r) {
        String method = buildVerificationMethod(r);
        return new AttendanceRecordResponse(
            r.getId(),
            r.getSession().getId(),
            r.getStudent().getId(),
            r.getSession().getCourse().getId(),
            r.getStudent().getName(),
            r.getStudent().getRollNumber(),
            r.getStatus(),
            Boolean.TRUE.equals(r.getFaceVerified()),
            Boolean.TRUE.equals(r.getGpsVerified()),
            Boolean.TRUE.equals(r.getDeviceVerified()),
            Boolean.TRUE.equals(r.getLivenessVerified()),
            Boolean.TRUE.equals(r.getBeaconVerified()),
            Boolean.TRUE.equals(r.getManuallyOverridden()),
            r.getOverrideReason(),
            r.getMarkedAt(),
            r.getSession().getCourse().getName(),
            r.getSession().getCourse().getCode(),
            r.getSession().getTitle(),
            method
        );
    }

    private static String buildVerificationMethod(AttendanceRecord r) {
        StringBuilder sb = new StringBuilder();
        if (Boolean.TRUE.equals(r.getFaceVerified()))      sb.append("Face");
        if (Boolean.TRUE.equals(r.getLivenessVerified()))  sb.append(sb.isEmpty() ? "Liveness" : " + Liveness");
        if (Boolean.TRUE.equals(r.getGpsVerified()))       sb.append(sb.isEmpty() ? "GPS" : " + GPS");
        if (Boolean.TRUE.equals(r.getBeaconVerified()))    sb.append(sb.isEmpty() ? "BLE" : " + BLE");
        if (Boolean.TRUE.equals(r.getDeviceVerified()))    sb.append(sb.isEmpty() ? "Device" : " + Device");
        return sb.isEmpty() ? "Manual" : sb.toString();
    }
}
