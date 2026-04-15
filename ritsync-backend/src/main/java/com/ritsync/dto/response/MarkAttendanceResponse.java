package com.ritsync.dto.response;

import com.ritsync.enums.AttendanceStatus;

public record MarkAttendanceResponse(
    Long             recordId,
    AttendanceStatus status,
    boolean          faceVerified,
    boolean          gpsVerified,
    boolean          deviceVerified,
    boolean          allVerified,
    String           message,
    double           faceDistance,
    double           gpsDistanceMetres
) {}
