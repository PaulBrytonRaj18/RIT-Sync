package com.ritsync.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateCourseRequest(
    @NotBlank String code,
    @NotBlank String name,
    @NotBlank String department,
    @NotNull  Integer creditHours,
    String semester,
    String academicYear
) {}
