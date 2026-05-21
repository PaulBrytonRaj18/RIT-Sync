package com.ritsync.dto.response;

import java.util.List;

public record TeacherDashboardResponse(
    List<CourseTile> courses,
    List<SessionTile> todaySessions,
    List<SessionTile> pendingAttendance,
    List<HandledSection> handledSections,
    int totalStudents,
    int liveSessions
) {
    public record CourseTile(Long id, String code, String name, String department, int enrolledCount) {}
    public record SessionTile(Long id, Long courseId, String courseName, String courseCode, String title,
                              String startTime, String endTime, boolean isOpen) {}
    public record HandledSection(Long courseId, String courseCode, String courseName, String section, int studentCount) {}
}
