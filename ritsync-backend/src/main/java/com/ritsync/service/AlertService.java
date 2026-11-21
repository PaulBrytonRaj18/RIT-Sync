package com.ritsync.service;

import com.ritsync.entity.Course;
import com.ritsync.entity.Notification;
import com.ritsync.entity.User;
import com.ritsync.enums.NotificationType;
import com.ritsync.repository.CourseRepository;
import com.ritsync.repository.NotificationRepository;
import com.ritsync.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.Collection;

/**
 * Low-attendance alerts. Checked when a student's percentage can actually drop (a session closes,
 * or a teacher overrides a record to ABSENT) — never after a successful check-in.
 * Each level is raised at most once per student per course per cooldown window; every alert is
 * stored as a {@link Notification} and also emailed when mail is configured.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    private final AttendanceStatsService  statsService;
    private final UserRepository          userRepo;
    private final CourseRepository        courseRepo;
    private final NotificationRepository  notificationRepo;
    private final JavaMailSender          mailSender;

    @Value("${app.attendance.threshold-critical-percent:65}")
    private double criticalPercent;

    @Value("${app.attendance.threshold-warning-percent:75}")
    private double warningPercent;

    /** Don't alert on the first couple of classes — one absence would look like 0 %. */
    @Value("${app.attendance.alert-min-sessions:3}")
    private int minSessions;

    @Value("${app.attendance.alert-cooldown-days:7}")
    private int cooldownDays;

    @Value("${spring.mail.username:}")
    private String mailFrom;

    /** Published when attendance may have dropped (a session closed, or a record overridden). */
    public record AttendanceChangedEvent(Collection<Long> studentIds, Long courseId) {}

    /** Runs after the publishing transaction commits, off the request thread. */
    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onAttendanceChanged(AttendanceChangedEvent event) {
        for (Long studentId : event.studentIds()) {
            try {
                checkAndAlert(studentId, event.courseId());
            } catch (Exception e) {
                log.error("Alert check failed for student {} course {}", studentId, event.courseId(), e);
            }
        }
    }

    /** Returns the alert raised, if any (visible for testing). */
    public NotificationType checkAndAlert(Long studentId, Long courseId) {
        var counts = statsService.forStudent(studentId, courseId);
        if (counts.counted() < minSessions) return null;

        double pct = counts.percentage();
        NotificationType type = pct < criticalPercent ? NotificationType.THRESHOLD_CRITICAL
                              : pct < warningPercent  ? NotificationType.THRESHOLD_WARNING
                              : null;
        if (type == null) return null;

        LocalDateTime since = LocalDateTime.now().minusDays(cooldownDays);
        if (notificationRepo.existsByUserIdAndCourseIdAndTypeAndCreatedAtAfter(studentId, courseId, type, since)) {
            return null; // already told them recently
        }

        User student = userRepo.findById(studentId).orElse(null);
        Course course = courseRepo.findById(courseId).orElse(null);
        if (student == null || course == null) return null;

        long attended = counts.present() + counts.late();
        String title = type == NotificationType.THRESHOLD_CRITICAL
            ? "Critical: attendance below " + fmt(criticalPercent) + "% in " + course.getCode()
            : "Attendance warning for " + course.getCode();
        String body = type == NotificationType.THRESHOLD_CRITICAL
            ? String.format("Your attendance in %s (%s) is %.1f%%, below the minimum of %s%%. "
                + "You need to attend the next %d classes in a row to get back to %s%%. Please contact your instructor.",
                course.getName(), course.getCode(), pct, fmt(criticalPercent),
                classesNeeded(attended, counts.counted(), criticalPercent), fmt(criticalPercent))
            : String.format("Your attendance in %s (%s) is %.1f%%. The expected minimum is %s%%. "
                + "Please make sure you attend upcoming classes.",
                course.getName(), course.getCode(), pct, fmt(warningPercent));

        Notification n = notificationRepo.save(Notification.builder()
            .userId(studentId).courseId(courseId).type(type).title(title).message(body).build());

        if (sendEmail(student.getEmail(), title, "Dear " + student.getName() + ",\n\n" + body + "\n\n— RIT-Sync")) {
            n.setEmailSent(true);
            notificationRepo.save(n);
        }
        log.info("Raised {} for student {} course {} ({}%)", type, studentId, courseId, String.format("%.1f", pct));
        return type;
    }

    private boolean sendEmail(String to, String subject, String body) {
        if (mailFrom == null || mailFrom.isBlank()) {
            return false; // mail not configured: in-app notification only
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(mailFrom);
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            mailSender.send(msg);
            return true;
        } catch (Exception e) {
            log.warn("Failed to send alert email to user: {}", e.getMessage());
            return false;
        }
    }

    /** Consecutive classes needed so that attended / counted reaches targetPct. */
    static int classesNeeded(long attended, long counted, double targetPct) {
        double t = targetPct / 100.0;
        if (t >= 1.0) return Integer.MAX_VALUE;
        double needed = (t * counted - attended) / (1 - t);
        return (int) Math.max(0, Math.ceil(needed));
    }

    private static String fmt(double pct) {
        return pct == Math.rint(pct) ? String.valueOf((long) pct) : String.valueOf(pct);
    }
}
