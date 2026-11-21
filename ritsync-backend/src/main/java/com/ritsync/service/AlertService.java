package com.ritsync.service;

import com.ritsync.entity.User;
import com.ritsync.enums.AttendanceStatus;
import com.ritsync.repository.AttendanceRecordRepository;
import com.ritsync.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertService {

    private final AttendanceRecordRepository recordRepo;
    private final UserRepository             userRepo;
    private final JavaMailSender             mailSender;
    private final AttendanceStatsService     statsService;

    @Value("${app.attendance.threshold-critical-percent:65}")
    private int criticalPercent;

    @Value("${app.attendance.threshold-warning-percent:75}")
    private int warningPercent;

    @Async
    public void checkAndAlertAsync(Long studentId, Long courseId) {
        try {
            var counts = statsService.forStudent(studentId, courseId);
            if (counts.counted() < 5) return;
            long present = counts.present() + counts.late();
            long total   = counts.counted();
            double pct   = counts.percentage();

            if (pct < criticalPercent) {
                sendCriticalAlert(studentId, courseId, pct, present, total);
            } else if (pct < warningPercent) {
                sendWarningAlert(studentId, courseId, pct, present, total);
            }
        } catch (Exception e) {
            log.error("Alert check failed for student {}: {}", studentId, e.getMessage());
        }
    }

    private void sendCriticalAlert(Long studentId, Long courseId, double pct, long present, long total) {
        User student = userRepo.findById(studentId).orElse(null);
        if (student == null) return;

        int classesNeeded = calculateClassesNeeded(present, total, criticalPercent);

        String body = String.format("""
            Dear %s,

            Your attendance has dropped to %.1f%%, which is below the minimum required %d%%.

            You need to attend at least %d consecutive classes to reach the minimum threshold.

            Please contact your instructor immediately.

            — RIT-Sync Attendance System
            """, student.getName(), pct, criticalPercent, classesNeeded);

        sendEmail(student.getEmail(), "⚠️ Critical: Attendance below minimum — RIT-Sync", body);
    }

    private void sendWarningAlert(Long studentId, Long courseId, double pct, long present, long total) {
        User student = userRepo.findById(studentId).orElse(null);
        if (student == null) return;

        String body = String.format("""
            Dear %s,

            Your attendance is currently %.1f%%.

            The minimum requirement is %d%%. Please ensure you attend upcoming classes.

            — RIT-Sync Attendance System
            """, student.getName(), pct, criticalPercent);

        sendEmail(student.getEmail(), "Attendance warning — RIT-Sync", body);
    }

    private void sendEmail(String to, String subject, String body) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            msg.setFrom("noreply@ritsync.app");
            mailSender.send(msg);
            log.info("Alert email sent to {}", to);
        } catch (Exception e) {
            log.warn("Failed to send alert email to {}: {}", to, e.getMessage());
        }
    }

    private int calculateClassesNeeded(long present, long total, int targetPct) {
        int x = 0;
        while (total + x > 0 && (double)(present + x) / (total + x) * 100 < targetPct) {
            x++;
        }
        return x;
    }
}
