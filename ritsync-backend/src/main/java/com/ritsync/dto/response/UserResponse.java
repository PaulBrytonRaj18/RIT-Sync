package com.ritsync.dto.response;

import com.ritsync.entity.User;
import com.ritsync.enums.Role;
import org.hibernate.Hibernate;

import java.time.LocalDateTime;

public record UserResponse(
    Long          id,
    String        name,
    String        email,
    Role          role,
    String        department,
    String        rollNumber,
    String        employeeId,
    String        avatarUrl,
    boolean       faceEnrolled,
    LocalDateTime createdAt
) {
    public static UserResponse from(User u, boolean faceEnrolled) {
        return new UserResponse(
            u.getId(), u.getName(), u.getEmail(), u.getRole(),
            u.getDepartment(), u.getRollNumber(), u.getEmployeeId(),
            u.getAvatarUrl(),
            faceEnrolled,
            u.getCreatedAt()
        );
    }
}
