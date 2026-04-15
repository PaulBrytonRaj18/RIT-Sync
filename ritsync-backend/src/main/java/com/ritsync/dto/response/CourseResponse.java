package com.ritsync.dto.response;

import com.ritsync.entity.Course;

import java.time.LocalDateTime;

public record CourseResponse(
    Long          id,
    String        code,
    String        name,
    Long          instructorId,
    String        instructorName,
    String        department,
    Integer       creditHours,
    String        semester,
    String        academicYear,
    int           enrolledCount,
    LocalDateTime createdAt
) {
    public static CourseResponse from(Course c, int enrolledCount) {
        return new CourseResponse(
            c.getId(),
            c.getCode(),
            c.getName(),
            c.getInstructor().getId(),
            c.getInstructor().getName(),
            c.getDepartment(),
            c.getCreditHours(),
            c.getSemester(),
            c.getAcademicYear(),
            enrolledCount,
            c.getCreatedAt()
        );
    }
}
