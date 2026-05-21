package com.ritsync.dto.response;

import java.util.List;

public record SubjectAttendanceSummaryResponse(
    Long courseId,
    String courseName,
    String courseCode,
    long totalSessions,
    List<StudentSummary> students
) {
    public record StudentSummary(
        Long studentId,
        String studentName,
        String rollNumber,
        long present,
        long absent,
        long late,
        long excused,
        double percentage,
        boolean atRisk
    ) {}
}
