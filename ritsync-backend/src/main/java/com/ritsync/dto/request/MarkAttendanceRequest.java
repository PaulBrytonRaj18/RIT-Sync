package com.ritsync.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

public record MarkAttendanceRequest(

    @NotNull  Long sessionId,

    /** 128-d face-api.js descriptor captured live on the device. */
    @NotNull @Size(min = 128, max = 128, message = "faceDescriptor must have exactly 128 values")
    List<Double> faceDescriptor,

    /**
     * Facial landmark frames for server-side liveness analysis: each frame is 68 [x, y] points.
     * Required only when the session has liveness enabled.
     */
    @Size(max = 150, message = "Too many liveness frames")
    List<List<List<Double>>> livenessFrames,

    @NotNull @DecimalMin("-90")  @DecimalMax("90")  Double userLat,
    @NotNull @DecimalMin("-180") @DecimalMax("180") Double userLng,
    @PositiveOrZero Double gpsAccuracy,

    @Size(max = 64)  String beaconUuid,
    @Size(max = 512) String beaconToken,
              Integer beaconRssi,

    @NotBlank @Size(max = 128) String deviceFingerprint,
    @Size(max = 120) String deviceName

) {}
