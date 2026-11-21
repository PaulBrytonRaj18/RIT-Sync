package com.ritsync.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ritsync.entity.User;
import com.ritsync.enums.Role;
import com.ritsync.repository.UserRepository;
import com.ritsync.security.JwtTokenProvider;
import com.ritsync.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** One percentage definition everywhere: ended sessions only, EXCUSED excluded, LATE counts as attended. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AttendanceStatsIntegrationTest {

    private static final double LAT = 12.9716, LNG = 77.5946;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtTokenProvider tokens;

    private String teacher;
    private Long courseId;

    private String bearer(User u) {
        return "Bearer " + tokens.generateAccessToken(new UserPrincipal(u));
    }

    private User user(Role role) {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        return users.save(User.builder().name(role + " " + tag).email(tag + "@ritsync.test")
            .passwordHash(encoder.encode("x-password")).role(role).department("CSE")
            .rollNumber(role == Role.STUDENT ? "R" + tag : null)
            .employeeId(role != Role.STUDENT ? "E" + tag : null).build());
    }

    private JsonNode read(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private JsonNode call(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b,
                          String auth, Object body) throws Exception {
        b.header("Authorization", auth);
        if (body != null) b.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return read(mvc.perform(b).andExpect(status().is2xxSuccessful()));
    }

    private Long session(LocalDateTime start, LocalDateTime end) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("courseId", courseId);
        body.put("startTime", start.toString());
        body.put("endTime", end.toString());
        body.put("geofenceLat", LAT);
        body.put("geofenceLng", LNG);
        return call(post("/sessions"), teacher, body).at("/data/id").asLong();
    }

    private void override(Long sessionId, Long studentId, String status) throws Exception {
        call(put("/attendance/override/" + sessionId + "/student/" + studentId), teacher,
            Map.of("status", status, "reason", "test"));
    }

    private void enroll(Long studentId) throws Exception {
        call(post("/courses/" + courseId + "/enroll/" + studentId), teacher, null);
    }

    @BeforeEach
    void setUp() throws Exception {
        teacher = bearer(user(Role.TEACHER));
        courseId = call(post("/courses"), teacher, Map.of(
            "code", "C" + UUID.randomUUID().toString().substring(0, 6), "name", "Stats 101",
            "department", "CSE", "creditHours", 3)).at("/data/id").asLong();
    }

    @Test
    void percentageIgnoresFutureSessionsAndExcusedOnesAndIsConsistentAcrossScreens() throws Exception {
        User student = user(Role.STUDENT);
        enroll(student.getId());

        LocalDateTime now = LocalDateTime.now();
        Long attended = session(now.minusDays(3), now.minusDays(3).plusHours(1));
        session(now.minusDays(2), now.minusDays(2).plusHours(1));               // stays ABSENT
        Long excused  = session(now.minusDays(1), now.minusDays(1).plusHours(1));
        session(now.plusDays(1), now.plusDays(1).plusHours(1));                  // future: not counted
        override(attended, student.getId(), "PRESENT");
        override(excused,  student.getId(), "EXCUSED");

        // Student's own stats: 1 present of 2 counted sessions = 50 %
        JsonNode stats = call(get("/attendance/stats/" + student.getId() + "/course/" + courseId),
            bearer(student), null).get("data");
        assertThat(stats.get("percentage").asDouble()).isEqualTo(50.0);
        assertThat(stats.get("present").asLong()).isEqualTo(1);
        assertThat(stats.get("absent").asLong()).isEqualTo(1);
        assertThat(stats.get("excused").asLong()).isEqualTo(1);

        // Teacher's subject view agrees
        JsonNode subject = call(get("/teacher/courses/" + courseId + "/attendance"), teacher, null).get("data");
        assertThat(subject.get("totalSessions").asLong()).isEqualTo(3); // ended sessions
        JsonNode row = subject.get("students").get(0);
        assertThat(row.get("percentage").asDouble()).isEqualTo(50.0);
        assertThat(row.get("atRisk").asBoolean()).isTrue();

        // Class dashboard agrees and lists the student as a defaulter
        JsonNode dash = call(get("/teacher/class-dashboard"), teacher, null).get("data");
        assertThat(dash.get("defaulterCount").asLong()).isEqualTo(1);
        assertThat(dash.at("/defaulters/0/overallPct").asDouble()).isEqualTo(50.0);
        assertThat(dash.at("/courseSummaries/0/avgAttendancePct").asDouble()).isEqualTo(50.0);
    }

    @Test
    void studentWithZeroPercentIsADefaulter() throws Exception {
        User student = user(Role.STUDENT);
        enroll(student.getId());
        LocalDateTime now = LocalDateTime.now();
        session(now.minusDays(1), now.minusDays(1).plusHours(1));

        JsonNode dash = call(get("/teacher/class-dashboard"), teacher, null).get("data");
        assertThat(dash.get("defaulterCount").asLong()).isEqualTo(1);
        assertThat(dash.at("/defaulters/0/overallPct").asDouble()).isEqualTo(0.0);
    }

    @Test
    void midTermEnrollmentOnlyCountsSessionsFromNowOn() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        session(now.minusDays(2), now.minusDays(2).plusHours(1));   // before the student joined
        Long upcoming = session(now.plusHours(1), now.plusHours(2));

        User late = user(Role.STUDENT);
        enroll(late.getId());

        // A placeholder exists for the upcoming session, none for the past one
        JsonNode roster = call(get("/attendance/session/" + upcoming), teacher, null).get("data");
        assertThat(roster.size()).isEqualTo(1);

        JsonNode stats = call(get("/attendance/stats/" + late.getId() + "/course/" + courseId),
            bearer(late), null).get("data");
        assertThat(stats.get("totalSessions").asLong()).isZero();

        JsonNode dash = call(get("/teacher/class-dashboard"), teacher, null).get("data");
        assertThat(dash.get("defaulterCount").asLong()).isZero();
    }

    @Test
    void checkInLongAfterStartIsLate() throws Exception {
        User student = user(Role.STUDENT);
        String auth = bearer(student);
        enroll(student.getId());

        List<Double> enrolled = new ArrayList<>();
        for (int i = 0; i < 128; i++) enrolled.add(Math.cos(i) * 0.1);
        mvc.perform(post("/users/me/face").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("descriptor", enrolled)))).andExpect(status().isCreated());

        LocalDateTime now = LocalDateTime.now();
        Long sessionId = session(now.minusMinutes(30), now.plusMinutes(30));
        call(patch("/sessions/" + sessionId + "/open"), teacher, null);

        List<Double> live = enrolled.stream().map(d -> d + 0.005).toList();
        Map<String, Object> body = new HashMap<>();
        body.put("sessionId", sessionId);
        body.put("faceDescriptor", live);
        body.put("userLat", LAT);
        body.put("userLng", LNG);
        body.put("gpsAccuracy", 10.0);
        body.put("deviceFingerprint", "first-device");
        mvc.perform(post("/attendance/mark").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
            .andExpect(jsonPath("$.data.allVerified").value(true))
            .andExpect(jsonPath("$.data.status").value("LATE"));

        // LATE counts as attended
        JsonNode stats = call(get("/attendance/stats/" + student.getId() + "/course/" + courseId), auth, null).get("data");
        assertThat(stats.get("percentage").asDouble()).isEqualTo(100.0);
    }
}
