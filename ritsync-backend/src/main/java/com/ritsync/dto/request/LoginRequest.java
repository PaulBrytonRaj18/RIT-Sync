package com.ritsync.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @Email @NotBlank String email,
    @NotBlank         String password,
    @NotBlank         String deviceFingerprint,
                      String deviceName
) {}
