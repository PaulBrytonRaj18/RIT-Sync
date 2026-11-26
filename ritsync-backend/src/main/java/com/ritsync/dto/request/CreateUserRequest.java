package com.ritsync.dto.request;

import com.ritsync.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Admin-only account creation (the only way to create TEACHER and ADMIN accounts). */
public record CreateUserRequest(
    @NotBlank @Size(max = 120)            String name,
    @Email @NotBlank @Size(max = 150)     String email,
    @NotBlank @Size(min = 8, max = 72)    String password,
    @NotNull                              Role   role,
    @NotBlank @Size(max = 100)            String department,
    @Size(max = 30)                       String rollNumber,
    @Size(max = 30)                       String employeeId,
    @Size(max = 20)                       String phone
) {}
