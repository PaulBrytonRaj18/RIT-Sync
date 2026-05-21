package com.ritsync.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record MarkAttendanceRequest(

    @NotNull  Long sessionId,

    @NotNull  List<Double> faceDescriptor,

              Boolean livenessVerified,
              Integer blinkCount,

    @NotNull  Double userLat,
    @NotNull  Double userLng,
              Double gpsAccuracy,

              String beaconUuid,
              String beaconToken,
              Integer beaconRssi,

    @NotBlank String deviceFingerprint,
              String deviceName

) {}
