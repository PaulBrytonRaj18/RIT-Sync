package com.ritsync.dto.request;

import com.ritsync.enums.Role;
import jakarta.validation.constraints.*;

public record RegisterRequest(
    @NotBlank                             String name,
    @Email @NotBlank                      String email,
    @NotBlank @Size(min = 8, max = 72)    String password,
    @NotNull                              Role   role,
    @NotBlank                             String department,
                                          String rollNumber,
                                          String employeeId,
                                          String phone
) {}
