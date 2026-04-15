package com.ritsync.dto.response;

import java.util.Map;

public record AttendanceStatsResponse(
    Long   studentId,
    Long   courseId,
    long   totalSessions,
    long   present,
    long   absent,
    long   late,
    long   excused,
    double percentage,
    Map<String, Long> breakdown
) {}
