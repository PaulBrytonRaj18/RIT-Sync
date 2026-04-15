package com.ritsync.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record MarkAttendanceRequest(

    @NotNull  Long sessionId,

    @NotNull  List<Double> faceDescriptor,

    @NotNull  Double userLat,
    @NotNull  Double userLng,
              Double gpsAccuracy,

    @NotBlank String deviceFingerprint,
              String deviceName

) {}
