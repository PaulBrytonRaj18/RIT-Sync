package com.ritsync.service;

import com.ritsync.enums.AttendanceStatus;
import com.ritsync.repository.AttendanceRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The single definition of "attendance percentage", used by every dashboard, stat and alert:
 * <pre>
 *   percentage = (PRESENT + LATE) / (sessions counted − EXCUSED) × 100
 * </pre>
 * A session is counted once it has ended (or earlier, if the student already has a non-ABSENT
 * status), so upcoming and in-progress classes never lower anyone's percentage, and approved
 * leave never counts against a student.
 */
@Service
@RequiredArgsConstructor
public class AttendanceStatsService {

    private final AttendanceRecordRepository attendanceRepo;

    public record Counts(long present, long late, long absent, long excused) {
        public static final Counts EMPTY = new Counts(0, 0, 0, 0);

        public long total()    { return present + late + absent + excused; }
        /** Sessions that count towards the percentage (excused ones don't). */
        public long counted()  { return present + late + absent; }
        public double percentage() {
            return counted() == 0 ? 0.0 : (present + late) * 100.0 / counted();
        }
        public boolean hasData() { return counted() > 0; }

        Counts plus(AttendanceStatus status, long n) {
            return switch (status) {
                case PRESENT -> new Counts(present + n, late, absent, excused);
                case LATE    -> new Counts(present, late + n, absent, excused);
                case ABSENT  -> new Counts(present, late, absent + n, excused);
                case EXCUSED -> new Counts(present, late, absent, excused + n);
            };
        }
    }

    @Transactional(readOnly = true)
    public Counts forStudent(Long studentId, Long courseId) {
        return fromStatusRows(attendanceRepo.countStatusesForStudent(
            studentId, courseId, LocalDateTime.now(), AttendanceStatus.ABSENT));
    }

    /** courseId → studentId → counts, computed with one grouped query. */
    @Transactional(readOnly = true)
    public Map<Long, Map<Long, Counts>> forCourses(List<Long> courseIds) {
        Map<Long, Map<Long, Counts>> result = new HashMap<>();
        if (courseIds.isEmpty()) return result;
        for (Object[] row : attendanceRepo.countStatusesPerCourseAndStudent(
                courseIds, LocalDateTime.now(), AttendanceStatus.ABSENT)) {
            Long courseId  = (Long) row[0];
            Long studentId = (Long) row[1];
            result.computeIfAbsent(courseId, k -> new HashMap<>())
                  .merge(studentId, Counts.EMPTY.plus((AttendanceStatus) row[2], (Long) row[3]),
                         AttendanceStatsService::sum);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Counts overall() {
        return fromStatusRows(attendanceRepo.countStatusesOverall(LocalDateTime.now(), AttendanceStatus.ABSENT));
    }

    /** Counts for sessions that started within [from, to). */
    @Transactional(readOnly = true)
    public Counts forSessionsStartingBetween(LocalDateTime from, LocalDateTime to) {
        return fromStatusRows(attendanceRepo.countStatusesForSessionsStartingBetween(
            from, to, LocalDateTime.now(), AttendanceStatus.ABSENT));
    }

    /** department → counts, by the student's department. */
    @Transactional(readOnly = true)
    public Map<String, Counts> byStudentDepartment() {
        Map<String, Counts> result = new HashMap<>();
        for (Object[] row : attendanceRepo.countStatusesByStudentDepartment(LocalDateTime.now(), AttendanceStatus.ABSENT)) {
            String dept = row[0] == null ? "Unassigned" : (String) row[0];
            result.merge(dept, Counts.EMPTY.plus((AttendanceStatus) row[1], (Long) row[2]), AttendanceStatsService::sum);
        }
        return result;
    }

    private static Counts fromStatusRows(List<Object[]> rows) {
        Counts c = Counts.EMPTY;
        for (Object[] row : rows) c = c.plus((AttendanceStatus) row[0], (Long) row[1]);
        return c;
    }

    private static Counts sum(Counts a, Counts b) {
        return new Counts(a.present + b.present, a.late + b.late, a.absent + b.absent, a.excused + b.excused);
    }
}
