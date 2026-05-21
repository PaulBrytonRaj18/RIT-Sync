package com.ritsync.dto.response;

import com.ritsync.entity.Session;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SessionResponse(
    Long          id,
    Long          courseId,
    String        courseName,
    String        courseCode,
    String        title,
    Long          instructorId,
    String        instructorName,
    LocalDateTime startTime,
    LocalDateTime endTime,
    BigDecimal    geofenceLat,
    BigDecimal    geofenceLng,
    int           geofenceRadiusM,
    boolean       isOpen,
    boolean       enableLiveness,
    boolean       enableBle,
    boolean       enableZkp,
    boolean       isMakeup,
    LocalDateTime createdAt
) {
    public static SessionResponse from(Session s) {
        return new SessionResponse(
            s.getId(),
            s.getCourse().getId(),
            s.getCourse().getName(),
            s.getCourse().getCode(),
            s.getTitle(),
            s.getCourse().getInstructor().getId(),
            s.getCourse().getInstructor().getName(),
            s.getStartTime(),
            s.getEndTime(),
            s.getGeofenceLat(),
            s.getGeofenceLng(),
            s.getGeofenceRadiusM(),
            Boolean.TRUE.equals(s.getIsOpen()),
            Boolean.TRUE.equals(s.getEnableLiveness()),
            Boolean.TRUE.equals(s.getEnableBle()),
            Boolean.TRUE.equals(s.getEnableZkp()),
            Boolean.TRUE.equals(s.getIsMakeup()),
            s.getCreatedAt()
        );
    }
}
