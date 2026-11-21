package com.ritsync.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ritsync.entity.User;
import com.ritsync.enums.Role;
import com.ritsync.repository.UserRepository;
import com.ritsync.security.JwtTokenProvider;
import com.ritsync.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminAnalyticsIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired JwtTokenProvider tokens;

    private User user(Role role, String dept) {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        return users.save(User.builder().name(role + tag).email(tag + "@admin.test").passwordHash("x")
            .role(role).department(dept).rollNumber("R" + tag).employeeId("E" + tag).build());
    }

    private String bearer(User u) { return "Bearer " + tokens.generateAccessToken(new UserPrincipal(u)); }

    private JsonNode ok(MockHttpServletRequestBuilder b, String auth, Object body) throws Exception {
        b.header("Authorization", auth);
        if (body != null) b.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return json.readTree(mvc.perform(b).andExpect(status().is2xxSuccessful())
            .andReturn().getResponse().getContentAsString()).get("data");
    }

    @Test
    void dailyAndDepartmentAnalyticsCountAbsencesAndUseTheStudentsDepartment() throws Exception {
        String dept = "DEPT-" + UUID.randomUUID().toString().substring(0, 5);
        String admin = bearer(user(Role.ADMIN, "IT"));
        String teacher = bearer(user(Role.TEACHER, "Physics")); // course dept differs from students'
        User s1 = user(Role.STUDENT, dept);
        User s2 = user(Role.STUDENT, dept);

        Long courseId = ok(post("/courses"), teacher, Map.of("code", "AN" + UUID.randomUUID().toString().substring(0, 4),
            "name", "Analytics", "department", "Physics", "creditHours", 3)).get("id").asLong();
        ok(post("/courses/" + courseId + "/enroll/" + s1.getId()), teacher, null);
        ok(post("/courses/" + courseId + "/enroll/" + s2.getId()), teacher, null);

        // One session six days ago (a day no other test uses): s1 present, s2 absent
        LocalDateTime start = LocalDate.now().minusDays(6).atTime(10, 0);
        Map<String, Object> session = new HashMap<>();
        session.put("courseId", courseId);
        session.put("startTime", start.toString());
        session.put("endTime", start.plusHours(1).toString());
        session.put("geofenceLat", 12.97);
        session.put("geofenceLng", 77.59);
        Long sessionId = ok(post("/sessions"), teacher, session).get("id").asLong();
        ok(put("/attendance/override/" + sessionId + "/student/" + s1.getId()), teacher,
            Map.of("status", "PRESENT", "reason", "test"));

        // Daily: the absence is counted (before, absences had no markedAt and were invisible → ~100 %)
        JsonNode daily = ok(get("/admin/analytics/daily"), admin, null);
        JsonNode sixDaysAgo = daily.get(0);
        assertThat(sixDaysAgo.get("total").asLong()).isEqualTo(2);
        assertThat(sixDaysAgo.get("attendance").asDouble()).isEqualTo(50.0);

        // Departments: grouped by the STUDENT's department, not the course's
        JsonNode depts = ok(get("/admin/analytics/departments"), admin, null);
        JsonNode entry = null;
        for (JsonNode d : depts) if (dept.equals(d.get("department").asText())) entry = d;
        assertThat(entry).isNotNull();
        assertThat(entry.get("attendance").asDouble()).isEqualTo(50.0);
        assertThat(entry.get("students").asLong()).isEqualTo(2);
    }

    @Test
    void courseDetailIsVisibleOnlyToParticipants() throws Exception {
        User teacherUser = user(Role.TEACHER, "CSE");
        String teacher = bearer(teacherUser);
        User enrolled = user(Role.STUDENT, "CSE");
        User outsider = user(Role.STUDENT, "CSE");
        Long courseId = ok(post("/courses"), teacher, Map.of("code", "CD" + UUID.randomUUID().toString().substring(0, 4),
            "name", "Detail", "department", "CSE", "creditHours", 3)).get("id").asLong();
        ok(post("/courses/" + courseId + "/enroll/" + enrolled.getId()), teacher, null);

        JsonNode course = ok(get("/courses/" + courseId), bearer(enrolled), null);
        assertThat(course.get("name").asText()).isEqualTo("Detail");
        mvc.perform(get("/courses/" + courseId).header("Authorization", bearer(outsider)))
            .andExpect(status().isForbidden());
        mvc.perform(get("/courses/999999").header("Authorization", teacher))
            .andExpect(status().isNotFound());
    }
}
