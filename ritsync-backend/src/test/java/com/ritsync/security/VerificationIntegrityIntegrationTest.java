package com.ritsync.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ritsync.entity.User;
import com.ritsync.enums.Role;
import com.ritsync.repository.UserRepository;
import com.ritsync.service.LivenessVerificationServiceTest;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end abuse cases for attendance verification and resource-level authorization.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VerificationIntegrityIntegrationTest {

    private static final double LAT = 12.9716, LNG = 77.5946;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtTokenProvider tokens;

    private String teacherA, teacherB, admin;
    private String studentToken;
    private Long studentId;
    private Long courseId;
    private static int descriptorSeed = 0;

    // ── fixtures ────────────────────────────────────────────────────────────

    private String staff(Role role) {
        User u = users.save(User.builder()
            .name(role + " user").email(role.name().toLowerCase() + "-" + UUID.randomUUID() + "@ritsync.test")
            .passwordHash(encoder.encode("irrelevant-password")).role(role).department("CSE")
            .employeeId("EMP-" + UUID.randomUUID().toString().substring(0, 6))
            .build());
        return "Bearer " + tokens.generateAccessToken(new UserPrincipal(u));
    }

    private record Student(Long id, String bearer) {}

    private Student registerStudent(String fingerprint) throws Exception {
        String email = "stu-" + UUID.randomUUID() + "@ritsync.test";
        String body = json.writeValueAsString(Map.of(
            "name", "Student", "email", email, "password", "Str0ngPassw0rd!",
            "department", "CSE", "rollNumber", "R" + UUID.randomUUID().toString().substring(0, 6),
            "deviceFingerprint", fingerprint, "deviceName", "Test phone"));
        JsonNode data = read(mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())).get("data");
        return new Student(data.at("/user/id").asLong(), "Bearer " + data.get("accessToken").asText());
    }

    private JsonNode read(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private MockHttpServletRequestBuilder withJson(MockHttpServletRequestBuilder b, String bearer, Object body) throws Exception {
        return b.header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(body));
    }

    private Long createCourse(String teacher) throws Exception {
        Map<String, Object> body = Map.of("code", "C" + UUID.randomUUID().toString().substring(0, 6),
            "name", "Course", "department", "CSE", "creditHours", 3);
        return read(mvc.perform(withJson(post("/courses"), teacher, body)).andExpect(status().isCreated()))
            .at("/data/id").asLong();
    }

    private Long createOpenSession(String teacher, Long course, boolean liveness, boolean ble) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("courseId", course);
        body.put("title", "Lecture");
        body.put("startTime", LocalDateTime.now().minusMinutes(10).toString());
        body.put("endTime", LocalDateTime.now().plusMinutes(50).toString());
        body.put("geofenceLat", LAT);
        body.put("geofenceLng", LNG);
        body.put("geofenceRadiusM", 100);
        body.put("enableLiveness", liveness);
        body.put("enableBle", ble);
        Long id = read(mvc.perform(withJson(post("/sessions"), teacher, body)).andExpect(status().isCreated()))
            .at("/data/id").asLong();
        mvc.perform(patch("/sessions/" + id + "/open").header("Authorization", teacher)).andExpect(status().isOk());
        return id;
    }

    private static List<Double> baseDescriptor() {
        List<Double> d = new ArrayList<>();
        for (int i = 0; i < 128; i++) d.add(Math.sin(i) * 0.1);
        return d;
    }

    /** A "fresh capture": same face, small natural variation, never byte-identical twice. */
    private static List<Double> liveDescriptor() {
        int seed = ++descriptorSeed;
        List<Double> d = new ArrayList<>(baseDescriptor());
        for (int i = 0; i < d.size(); i++) d.set(i, d.get(i) + 0.005 + seed * 1e-6);
        return d;
    }

    private Map<String, Object> markBody(Long sessionId, List<Double> descriptor, String fingerprint) {
        Map<String, Object> body = new HashMap<>();
        body.put("sessionId", sessionId);
        body.put("faceDescriptor", descriptor);
        body.put("userLat", LAT);
        body.put("userLng", LNG);
        body.put("gpsAccuracy", 15.0);
        body.put("deviceFingerprint", fingerprint);
        body.put("deviceName", "Test phone");
        return body;
    }

    @BeforeEach
    void setUp() throws Exception {
        teacherA = staff(Role.TEACHER);
        teacherB = staff(Role.TEACHER);
        admin = staff(Role.ADMIN);
        Student s = registerStudent("device-A");
        studentId = s.id();
        studentToken = s.bearer();
        courseId = createCourse(teacherA);
        mvc.perform(post("/courses/" + courseId + "/enroll/" + studentId).header("Authorization", teacherA))
            .andExpect(status().isOk());
        mvc.perform(withJson(post("/users/me/face"), studentToken, Map.of("descriptor", baseDescriptor())))
            .andExpect(status().isCreated());
    }

    // ── registration & staff accounts ───────────────────────────────────────

    @Test
    void cannotSelfRegisterAsTeacher() throws Exception {
        Map<String, Object> body = Map.of("name", "Mallory", "email", "m-" + UUID.randomUUID() + "@x.test",
            "password", "Str0ngPassw0rd!", "role", "TEACHER", "department", "CSE", "employeeId", "E1");
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminCanCreateTeacherButTeacherCannot() throws Exception {
        Map<String, Object> body = Map.of("name", "New Teacher", "email", "t-" + UUID.randomUUID() + "@x.test",
            "password", "Str0ngPassw0rd!", "role", "TEACHER", "department", "CSE", "employeeId", "E9");
        mvc.perform(withJson(post("/admin/users"), teacherA, body)).andExpect(status().isForbidden());
        mvc.perform(withJson(post("/admin/users"), admin, body)).andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.role").value("TEACHER"));
    }

    @Test
    void teacherCannotListStaffAccounts() throws Exception {
        mvc.perform(get("/users/role/ADMIN").header("Authorization", teacherA)).andExpect(status().isForbidden());
        mvc.perform(get("/users/role/STUDENT").header("Authorization", teacherA)).andExpect(status().isOk());
    }

    // ── resource ownership ─────────────────────────────────────────────────

    @Test
    void teacherCannotManageAnotherTeachersCourseOrSession() throws Exception {
        Long sessionId = createOpenSession(teacherA, courseId, false, false);

        mvc.perform(patch("/sessions/" + sessionId + "/close").header("Authorization", teacherB))
            .andExpect(status().isForbidden());
        mvc.perform(get("/attendance/session/" + sessionId).header("Authorization", teacherB))
            .andExpect(status().isForbidden());
        mvc.perform(withJson(put("/attendance/override/" + sessionId + "/student/" + studentId), teacherB,
                Map.of("status", "PRESENT", "reason", "friend")))
            .andExpect(status().isForbidden());
        mvc.perform(post("/courses/" + courseId + "/enroll/" + registerStudent("x").id()).header("Authorization", teacherB))
            .andExpect(status().isForbidden());
        mvc.perform(get("/analytics/heatmap/" + sessionId).header("Authorization", teacherB))
            .andExpect(status().isForbidden());
        mvc.perform(post("/beacons/generate/" + sessionId).header("Authorization", teacherB))
            .andExpect(status().isForbidden());

        // ...while the owner and an admin can
        mvc.perform(get("/attendance/session/" + sessionId).header("Authorization", teacherA)).andExpect(status().isOk());
        mvc.perform(patch("/sessions/" + sessionId + "/close").header("Authorization", admin)).andExpect(status().isOk());
    }

    @Test
    void overrideRequiresAReason() throws Exception {
        Long sessionId = createOpenSession(teacherA, courseId, false, false);
        mvc.perform(withJson(put("/attendance/override/" + sessionId + "/student/" + studentId), teacherA,
                Map.of("status", "PRESENT")))
            .andExpect(status().isBadRequest());
    }

    // ── marking attendance ─────────────────────────────────────────────────

    @Test
    void enrolledStudentWithAllChecksPassingIsPresent() throws Exception {
        Long sessionId = createOpenSession(teacherA, courseId, false, false);
        mvc.perform(withJson(post("/attendance/mark"), studentToken, markBody(sessionId, liveDescriptor(), "device-A")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.allVerified").value(true))
            .andExpect(jsonPath("$.data.status").value("PRESENT"));

        // and cannot mark twice
        mvc.perform(withJson(post("/attendance/mark"), studentToken, markBody(sessionId, liveDescriptor(), "device-A")))
            .andExpect(status().isConflict());
    }

    @Test
    void studentNotEnrolledCannotMarkOrSeeTheSession() throws Exception {
        Long sessionId = createOpenSession(teacherA, courseId, false, false);
        Student outsider = registerStudent("device-O");
        mvc.perform(withJson(post("/attendance/mark"), outsider.bearer(), markBody(sessionId, liveDescriptor(), "device-O")))
            .andExpect(status().isForbidden());
        mvc.perform(get("/sessions/" + sessionId).header("Authorization", outsider.bearer()))
            .andExpect(status().isForbidden());
        mvc.perform(get("/sessions/open").header("Authorization", outsider.bearer()))
            .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void replayingTheEnrolledTemplateFails() throws Exception {
        Long sessionId = createOpenSession(teacherA, courseId, false, false);
        mvc.perform(withJson(post("/attendance/mark"), studentToken, markBody(sessionId, baseDescriptor(), "device-A")))
            .andExpect(jsonPath("$.data.faceVerified").value(false))
            .andExpect(jsonPath("$.data.status").value("ABSENT"));
    }

    @Test
    void resubmittingAnIdenticalLiveCaptureFails() throws Exception {
        Long s1 = createOpenSession(teacherA, courseId, false, false);
        Long s2 = createOpenSession(teacherA, courseId, false, false);
        List<Double> captured = liveDescriptor();
        mvc.perform(withJson(post("/attendance/mark"), studentToken, markBody(s1, captured, "device-A")))
            .andExpect(jsonPath("$.data.allVerified").value(true));
        mvc.perform(withJson(post("/attendance/mark"), studentToken, markBody(s2, captured, "device-A")))
            .andExpect(jsonPath("$.data.faceVerified").value(false));
    }

    @Test
    void newDeviceIsNotTrustedUntilVerified() throws Exception {
        Long sessionId = createOpenSession(teacherA, courseId, false, false);
        mvc.perform(withJson(post("/attendance/mark"), studentToken, markBody(sessionId, liveDescriptor(), "friends-phone")))
            .andExpect(jsonPath("$.data.deviceVerified").value(false))
            .andExpect(jsonPath("$.data.allVerified").value(false));
    }

    @Test
    void outsideGeofenceOrImpreciseGpsFails() throws Exception {
        Long sessionId = createOpenSession(teacherA, courseId, false, false);
        var far = markBody(sessionId, liveDescriptor(), "device-A");
        far.put("userLat", LAT + 0.01); // ~1.1 km away
        mvc.perform(withJson(post("/attendance/mark"), studentToken, far))
            .andExpect(jsonPath("$.data.gpsVerified").value(false));

        var imprecise = markBody(sessionId, liveDescriptor(), "device-A");
        imprecise.put("gpsAccuracy", 2000.0);
        mvc.perform(withJson(post("/attendance/mark"), studentToken, imprecise))
            .andExpect(jsonPath("$.data.gpsVerified").value(false));
    }

    @Test
    void livenessIsEnforcedServerSideWhenTheSessionRequiresIt() throws Exception {
        Long sessionId = createOpenSession(teacherA, courseId, true, false);

        var noFrames = markBody(sessionId, liveDescriptor(), "device-A");
        noFrames.put("livenessVerified", true); // client claims are ignored
        mvc.perform(withJson(post("/attendance/mark"), studentToken, noFrames))
            .andExpect(jsonPath("$.data.livenessRequired").value(true))
            .andExpect(jsonPath("$.data.livenessVerified").value(false))
            .andExpect(jsonPath("$.data.allVerified").value(false));

        var withBlink = markBody(sessionId, liveDescriptor(), "device-A");
        withBlink.put("livenessFrames", LivenessVerificationServiceTest.blinkingSequence());
        mvc.perform(withJson(post("/attendance/mark"), studentToken, withBlink))
            .andExpect(jsonPath("$.data.livenessVerified").value(true))
            .andExpect(jsonPath("$.data.allVerified").value(true));
    }

    @Test
    void beaconIsEnforcedAndMustBelongToTheSession() throws Exception {
        Long bleSession = createOpenSession(teacherA, courseId, false, true);
        Long otherSession = createOpenSession(teacherA, courseId, false, true);

        // Omitting beacon data no longer skips the check
        mvc.perform(withJson(post("/attendance/mark"), studentToken, markBody(bleSession, liveDescriptor(), "device-A")))
            .andExpect(jsonPath("$.data.beaconVerified").value(false));

        // A valid beacon from a different session is rejected
        JsonNode other = read(mvc.perform(post("/beacons/generate/" + otherSession).header("Authorization", teacherA))).get("data");
        var wrong = markBody(bleSession, liveDescriptor(), "device-A");
        wrong.put("beaconUuid", other.get("uuid").asText());
        wrong.put("beaconToken", other.get("token").asText());
        wrong.put("beaconRssi", -60);
        mvc.perform(withJson(post("/attendance/mark"), studentToken, wrong))
            .andExpect(jsonPath("$.data.beaconVerified").value(false));

        // The session's own beacon works
        JsonNode own = read(mvc.perform(post("/beacons/generate/" + bleSession).header("Authorization", teacherA))).get("data");
        var right = markBody(bleSession, liveDescriptor(), "device-A");
        right.put("beaconUuid", own.get("uuid").asText());
        right.put("beaconToken", own.get("token").asText());
        right.put("beaconRssi", -60);
        mvc.perform(withJson(post("/attendance/mark"), studentToken, right))
            .andExpect(jsonPath("$.data.beaconVerified").value(true))
            .andExpect(jsonPath("$.data.allVerified").value(true));
    }

    @Test
    void attemptsAreCapped() throws Exception {
        Long sessionId = createOpenSession(teacherA, courseId, false, false);
        for (int i = 0; i < 5; i++) {
            mvc.perform(withJson(post("/attendance/mark"), studentToken, markBody(sessionId, liveDescriptor(), "wrong-device")))
                .andExpect(status().isOk());
        }
        mvc.perform(withJson(post("/attendance/mark"), studentToken, markBody(sessionId, liveDescriptor(), "device-A")))
            .andExpect(status().isTooManyRequests());
    }

    // ── face enrollment ────────────────────────────────────────────────────

    @Test
    void faceCanOnlyBeEnrolledOnceUntilAdminResets() throws Exception {
        mvc.perform(withJson(post("/users/me/face"), studentToken, Map.of("descriptor", liveDescriptor())))
            .andExpect(status().isConflict());
        mvc.perform(delete("/admin/users/" + studentId + "/face").header("Authorization", teacherA))
            .andExpect(status().isForbidden());
        mvc.perform(delete("/admin/users/" + studentId + "/face").header("Authorization", admin))
            .andExpect(status().isOk());
        mvc.perform(withJson(post("/users/me/face"), studentToken, Map.of("descriptor", liveDescriptor())))
            .andExpect(status().isCreated());
    }

    // ── leave requests ─────────────────────────────────────────────────────

    @Test
    void leaveRulesAreEnforced() throws Exception {
        String today = LocalDateTime.now().toLocalDate().toString();

        // Students can now reach the endpoint (previously blocked by the /teacher/** URL rule)
        JsonNode leave = read(mvc.perform(post("/teacher/leaves").header("Authorization", studentToken)
                .param("courseId", courseId.toString()).param("leaveType", "MEDICAL")
                .param("reason", "Fever").param("fromDate", today).param("toDate", today))
            .andExpect(status().isOk())).get("data");
        Long leaveId = leave.get("id").asLong();

        // Response is a DTO, not the entity (no password hashes leaking)
        mvc.perform(get("/teacher/leaves/my").header("Authorization", studentToken))
            .andExpect(jsonPath("$.data[0].studentName").exists())
            .andExpect(jsonPath("$.data[0].student").doesNotExist());

        mvc.perform(put("/teacher/leaves/" + leaveId + "/review").header("Authorization", teacherB).param("decision", "APPROVED"))
            .andExpect(status().isForbidden());
        mvc.perform(put("/teacher/leaves/" + leaveId + "/review").header("Authorization", teacherA).param("decision", "MAYBE"))
            .andExpect(status().isBadRequest());
        mvc.perform(put("/teacher/leaves/" + leaveId + "/review").header("Authorization", teacherA).param("decision", "APPROVED"))
            .andExpect(status().isOk());
        mvc.perform(put("/teacher/leaves/" + leaveId + "/review").header("Authorization", teacherA).param("decision", "REJECTED"))
            .andExpect(status().isConflict());

        // Not enrolled → cannot request leave for the course; bad date range rejected
        Student outsider = registerStudent("device-L");
        mvc.perform(post("/teacher/leaves").header("Authorization", outsider.bearer())
                .param("courseId", courseId.toString()).param("leaveType", "MEDICAL")
                .param("reason", "x").param("fromDate", today).param("toDate", today))
            .andExpect(status().isForbidden());
        mvc.perform(post("/teacher/leaves").header("Authorization", studentToken)
                .param("courseId", courseId.toString()).param("leaveType", "MEDICAL").param("reason", "x")
                .param("fromDate", today).param("toDate", LocalDateTime.now().minusDays(3).toLocalDate().toString()))
            .andExpect(status().isBadRequest());
    }
}
