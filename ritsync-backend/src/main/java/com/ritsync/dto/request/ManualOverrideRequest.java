package com.ritsync.dto.request;

import com.ritsync.enums.AttendanceStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ManualOverrideRequest(
    @NotNull AttendanceStatus status,
    /** Mandatory so every manual change is explained in the audit log. */
    @NotBlank @Size(max = 500) String reason
) {}
