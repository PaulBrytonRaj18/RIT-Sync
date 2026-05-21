package com.ritsync.dto.response;

import java.util.List;

public record ClassDashboardResponse(
    long totalStudents,
    double overallAttendancePct,
    long todayAbsentees,
    long defaulterCount,
    int courses,
    List<CourseAttendanceSummary> courseSummaries,
    List<DefaulterSummary> defaulters
) {
    public record CourseAttendanceSummary(
        Long courseId, String courseName, String courseCode,
        long totalStudents, double avgAttendancePct
    ) {}
    public record DefaulterSummary(
        Long studentId, String studentName, String rollNumber,
        double overallPct, List<CourseDeficit> deficits
    ) {}
    public record CourseDeficit(Long courseId, String courseName, double percentage) {}
}
