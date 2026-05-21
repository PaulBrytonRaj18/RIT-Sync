package com.ritsync.controller;

import com.ritsync.dto.response.ApiResponse;
import com.ritsync.enums.AttendanceStatus;
import com.ritsync.enums.Role;
import com.ritsync.repository.AttendanceRecordRepository;
import com.ritsync.repository.CourseRepository;
import com.ritsync.repository.UserRepository;
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
    private final AttendanceRecordRepository attendanceRepo;

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> getDashboard() {
        long studentCount = userRepository.countByRoleAndIsActiveTrue(Role.STUDENT);
        long teacherCount = userRepository.countByRoleAndIsActiveTrue(Role.TEACHER);
        long adminCount = userRepository.countByRoleAndIsActiveTrue(Role.ADMIN);
        long courseCount = courseRepository.count();

        long totalPresent = attendanceRepo.countByStatus(AttendanceStatus.PRESENT);
        long totalRecords = attendanceRepo.count();
        double avgAttendance = totalRecords == 0 ? 0.0 : totalPresent * 100.0 / totalRecords;

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

            long total = attendanceRepo.countByMarkedAtBetween(start, end);
            long present = attendanceRepo.countByMarkedAtBetweenAndStatus(start, end, AttendanceStatus.PRESENT);
            double pct = total == 0 ? 0.0 : present * 100.0 / total;

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
        List<Map<String, Object>> result = new ArrayList<>();

        for (String dept : departments) {
            long total = attendanceRepo.countByStudentDepartment(dept);
            long present = attendanceRepo.countByStudentDepartmentAndStatus(dept, AttendanceStatus.PRESENT);
            double pct = total == 0 ? 0.0 : present * 100.0 / total;

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("department", dept);
            entry.put("attendance", Math.round(pct * 10.0) / 10.0);
            entry.put("students", userRepository.countByDepartmentAndRoleAndIsActiveTrue(dept, Role.STUDENT));
            result.add(entry);
        }
        return ApiResponse.ok(result);
    }
}
