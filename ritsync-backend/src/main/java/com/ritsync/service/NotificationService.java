package com.ritsync.service;

import com.ritsync.entity.User;
import com.ritsync.enums.AttendanceStatus;
import com.ritsync.repository.AttendanceRecordRepository;
import com.ritsync.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final AttendanceRecordRepository attendanceRepo;
    private final UserRepository             userRepository;

    @Value("${app.attendance.threshold-warning-percent:75}")
    private double warningThreshold;

    @Value("${app.attendance.threshold-critical-percent:65}")
    private double criticalThreshold;

    @Async
    public void checkAndNotifyThreshold(Long studentId, Long courseId) {
        long total   = attendanceRepo.countTotalSessionsByStudentAndCourse(studentId, courseId);
        if (total == 0) return;

        long present = attendanceRepo.countByStudentAndCourseAndStatus(
            studentId, courseId, AttendanceStatus.PRESENT);
        long late    = attendanceRepo.countByStudentAndCourseAndStatus(
            studentId, courseId, AttendanceStatus.LATE);

        double pct = (present + late) * 100.0 / total;

        if (pct < criticalThreshold) {
            log.warn("CRITICAL threshold: student {} course {} at {:.1f}%", studentId, courseId, pct);
        } else if (pct < warningThreshold) {
            log.warn("WARNING threshold: student {} course {} at {:.1f}%", studentId, courseId, pct);
        }
    }
}
