package com.ritsync.controller;

import com.ritsync.dto.response.ApiResponse;
import com.ritsync.enums.Role;
import com.ritsync.repository.CourseRepository;
import com.ritsync.repository.UserRepository;
import com.ritsync.service.AttendanceStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final AttendanceStatsService statsService;

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> getDashboard() {
        long studentCount = userRepository.countByRoleAndIsActiveTrue(Role.STUDENT);
        long teacherCount = userRepository.countByRoleAndIsActiveTrue(Role.TEACHER);
        long adminCount = userRepository.countByRoleAndIsActiveTrue(Role.ADMIN);
        long courseCount = courseRepository.count();

        double avgAttendance = statsService.overall().percentage();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalStudents", studentCount);
        data.put("totalTeachers", teacherCount);
        data.put("totalAdmins", adminCount);
        data.put("totalCourses", courseCount);
        data.put("avgAttendance", Math.round(avgAttendance * 10.0) / 10.0);
        return ApiResponse.ok(data);
    }

    @GetMapping("/analytics/daily")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<Map<String, Object>>> getDailyAnalytics() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            LocalDateTime start = date.atStartOfDay();
            LocalDateTime end = date.plusDays(1).atStartOfDay();

            // Sessions held that day (absences have no markedAt, so group by session start)
            var c = statsService.forSessionsStartingBetween(start, end);
            long total = c.counted();
            long present = c.present() + c.late();
            double pct = c.percentage();

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("day", date.getDayOfWeek().name().substring(0, 3));
            entry.put("attendance", Math.round(pct * 10.0) / 10.0);
            entry.put("total", total);
            entry.put("present", present);
            result.add(entry);
        }
        return ApiResponse.ok(result);
    }

    @GetMapping("/analytics/departments")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<Map<String, Object>>> getDepartmentTrends() {
        List<String> departments = userRepository.findDistinctDepartments();
        Map<String, AttendanceStatsService.Counts> byDept = statsService.byStudentDepartment();
        List<Map<String, Object>> result = new ArrayList<>();

        for (String dept : departments) {
            double pct = byDept.getOrDefault(dept, AttendanceStatsService.Counts.EMPTY).percentage();

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("department", dept);
            entry.put("attendance", Math.round(pct * 10.0) / 10.0);
            entry.put("students", userRepository.countByDepartmentAndRoleAndIsActiveTrue(dept, Role.STUDENT));
            result.add(entry);
        }
        return ApiResponse.ok(result);
    }
}
