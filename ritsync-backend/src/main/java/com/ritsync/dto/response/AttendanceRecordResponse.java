package com.ritsync.dto.response;

import com.ritsync.entity.AttendanceRecord;
import com.ritsync.enums.AttendanceStatus;
import java.time.LocalDateTime;

public record AttendanceRecordResponse(
    Long             id,
    Long             sessionId,
    Long             studentId,
    String           studentName,
    String           studentRollNumber,
    AttendanceStatus status,
    boolean          faceVerified,
    boolean          gpsVerified,
    boolean          deviceVerified,
    boolean          manuallyOverridden,
    String           overrideReason,
    LocalDateTime    markedAt
) {
    public static AttendanceRecordResponse from(AttendanceRecord r) {
        return new AttendanceRecordResponse(
            r.getId(),
            r.getSession().getId(),
            r.getStudent().getId(),
            r.getStudent().getName(),
            r.getStudent().getRollNumber(),
            r.getStatus(),
            Boolean.TRUE.equals(r.getFaceVerified()),
            Boolean.TRUE.equals(r.getGpsVerified()),
            Boolean.TRUE.equals(r.getDeviceVerified()),
            Boolean.TRUE.equals(r.getManuallyOverridden()),
            r.getOverrideReason(),
            r.getMarkedAt()
        );
    }
}
