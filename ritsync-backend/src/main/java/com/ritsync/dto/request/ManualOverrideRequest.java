package com.ritsync.dto.request;

import com.ritsync.enums.AttendanceStatus;
import jakarta.validation.constraints.NotNull;

public record ManualOverrideRequest(
    @NotNull AttendanceStatus status,
    String reason
) {}
