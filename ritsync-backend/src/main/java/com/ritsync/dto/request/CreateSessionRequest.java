package com.ritsync.dto.request;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateSessionRequest(
    @NotNull Long      courseId,
             String    title,
    @NotNull LocalDateTime startTime,
    @NotNull LocalDateTime endTime,
    @NotNull BigDecimal    geofenceLat,
    @NotNull BigDecimal    geofenceLng,
             Integer       geofenceRadiusM
) {}
