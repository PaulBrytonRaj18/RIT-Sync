package com.ritsync.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ritsync.entity.Notification;
import com.ritsync.entity.User;
import com.ritsync.enums.NotificationType;
import com.ritsync.enums.Role;
import com.ritsync.repository.NotificationRepository;
import com.ritsync.repository.UserRepository;
import com.ritsync.security.JwtTokenProvider;
import com.ritsync.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AlertIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired NotificationRepository notifications;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtTokenProvider tokens;

    @Test
    void classesNeededMath() {
        assertThat(AlertService.classesNeeded(1, 4, 65)).isEqualTo(5);  // 6/9 = 66.7 %
        assertThat(AlertService.classesNeeded(3, 4, 75)).isZero();      // already at 75 %
        assertThat(AlertService.classesNeeded(0, 3, 75)).isEqualTo(9);  // 9/12 = 75 %
    }

    private User user(Role role) {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        return users.save(User.builder().name(role + " " + tag).email(tag + "@ritsync.test")
            .passwordHash(encoder.encode("x-password")).role(role).department("CSE")
            .rollNumber("R" + tag).employeeId("E" + tag).build());
    }

    private String bearer(User u) {
        return "Bearer " + tokens.generateAccessToken(new UserPrincipal(u));
    }

    private Long createSession(String teacher, Long courseId, LocalDateTime start, LocalDateTime end) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("courseId", courseId);
        body.put("startTime", start.toString());
        body.put("endTime", end.toString());
        body.put("geofenceLat", 12.97);
        body.put("geofenceLng", 77.59);
        String res = mvc.perform(post("/sessions").header("Authorization", teacher)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(res).at("/data/id").asLong();
    }

    private List<Notification> waitForAlerts(Long studentId, int expected) throws InterruptedException {
        List<Notification> found = List.of();
        for (int i = 0; i < 50; i++) {
            found = notifications.findByUserIdOrderByCreatedAtDesc(studentId);
            if (found.size() >= expected) break;
            Thread.sleep(100);
        }
        return found;
    }

    @Test
    void closingASessionAlertsAbsentStudentsOnceAndWithoutSpamming() throws Exception {
        User teacherUser = user(Role.TEACHER);
        String teacher = bearer(teacherUser);
        User student = user(Role.STUDENT);
        String res = mvc.perform(post("/courses").header("Authorization", teacher).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("code", "AL" + UUID.randomUUID().toString().substring(0, 4),
                    "name", "Alerts", "department", "CSE", "creditHours", 3))))
            .andReturn().getResponse().getContentAsString();
        Long courseId = json.readTree(res).at("/data/id").asLong();
        mvc.perform(post("/courses/" + courseId + "/enroll/" + student.getId()).header("Authorization", teacher))
            .andExpect(status().isOk());

        LocalDateTime now = LocalDateTime.now();
        for (int d = 3; d >= 1; d--) {
            createSession(teacher, courseId, now.minusDays(d), now.minusDays(d).plusHours(1)); // all missed
        }
        Long today = createSession(teacher, courseId, now.minusHours(2), now.minusHours(1));

        mvc.perform(patch("/sessions/" + today + "/open").header("Authorization", teacher)).andExpect(status().isOk());
        mvc.perform(patch("/sessions/" + today + "/close").header("Authorization", teacher)).andExpect(status().isOk());

        List<Notification> alerts = waitForAlerts(student.getId(), 1);
        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getType()).isEqualTo(NotificationType.THRESHOLD_CRITICAL);
        assertThat(alerts.get(0).getCourseId()).isEqualTo(courseId);
        assertThat(alerts.get(0).getEmailSent()).isFalse(); // mail not configured in tests

        // Closing again within the cooldown must not produce a second alert
        mvc.perform(patch("/sessions/" + today + "/open").header("Authorization", teacher)).andExpect(status().isOk());
        mvc.perform(patch("/sessions/" + today + "/close").header("Authorization", teacher)).andExpect(status().isOk());
        Thread.sleep(800);
        assertThat(notifications.findByUserIdOrderByCreatedAtDesc(student.getId())).hasSize(1);
    }
}
