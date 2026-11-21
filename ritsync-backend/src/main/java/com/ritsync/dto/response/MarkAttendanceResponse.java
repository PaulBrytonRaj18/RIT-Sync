package com.ritsync.dto.response;

import com.ritsync.enums.AttendanceStatus;

/**
 * Result of one attendance attempt. Each {@code *Verified} flag is {@code true} when that check
 * passed <em>or was not required by the session</em> (see {@code livenessRequired}/{@code beaconRequired}).
 */
public record MarkAttendanceResponse(
    Long             recordId,
    AttendanceStatus status,
    boolean          faceVerified,
    boolean          gpsVerified,
    boolean          deviceVerified,
    boolean          livenessVerified,
    boolean          beaconVerified,
    boolean          allVerified,
    String           message,
    double           faceDistance,
    double           gpsDistanceMetres,
    boolean          livenessRequired,
    boolean          beaconRequired,
    int              attemptsRemaining
) {}
