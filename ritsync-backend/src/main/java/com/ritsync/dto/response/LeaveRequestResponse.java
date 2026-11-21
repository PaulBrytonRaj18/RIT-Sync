package com.ritsync.dto.response;

import com.ritsync.entity.LeaveRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Safe projection of a leave request (never serialise the entity: it drags in full User objects). */
public record LeaveRequestResponse(
    Long          id,
    Long          studentId,
    String        studentName,
    String        studentRollNumber,
    Long          courseId,
    String        courseCode,
    String        courseName,
    String        leaveType,
    String        reason,
    LocalDate     fromDate,
    LocalDate     toDate,
    String        status,
    String        reviewedByName,
    String        reviewNotes,
    LocalDateTime createdAt
) {
    public static LeaveRequestResponse from(LeaveRequest lr) {
        var s = lr.getStudent();
        var c = lr.getCourse();
        var r = lr.getReviewedBy();
        return new LeaveRequestResponse(
            lr.getId(),
            s.getId(), s.getName(), s.getRollNumber(),
            c != null ? c.getId() : null, c != null ? c.getCode() : null, c != null ? c.getName() : null,
            lr.getLeaveType().name(), lr.getReason(), lr.getFromDate(), lr.getToDate(),
            lr.getStatus().name(),
            r != null ? r.getName() : null, lr.getReviewNotes(),
            lr.getCreatedAt());
    }
}
