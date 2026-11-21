package com.ritsync.config;

import com.ritsync.entity.Course;
import com.ritsync.entity.Session;
import com.ritsync.entity.User;
import com.ritsync.enums.Role;
import com.ritsync.repository.CourseRepository;
import com.ritsync.repository.SessionRepository;
import com.ritsync.repository.UserRepository;
import com.ritsync.security.JwtTokenProvider;
import com.ritsync.security.UserPrincipal;
import com.ritsync.service.HeatmapPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class HeatmapWebSocketIntegrationTest {

    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired CourseRepository courses;
    @Autowired SessionRepository sessions;
    @Autowired JwtTokenProvider tokens;
    @Autowired HeatmapPublisher publisher;

    private WebSocketStompClient client;
    private String ownerToken, otherToken;
    private Long sessionId;

    private User user(Role role) {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        return users.save(User.builder().name(role + tag).email(tag + "@ws.test").passwordHash("x")
            .role(role).department("CSE").employeeId("E" + tag).build());
    }

    @BeforeEach
    void setUp() {
        client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new MappingJackson2MessageConverter());

        User owner = user(Role.TEACHER);
        User other = user(Role.TEACHER);
        ownerToken = tokens.generateAccessToken(new UserPrincipal(owner));
        otherToken = tokens.generateAccessToken(new UserPrincipal(other));

        Course course = courses.save(Course.builder().code("WS" + UUID.randomUUID().toString().substring(0, 4))
            .name("Sockets").instructor(owner).department("CSE").creditHours(3).build());
        Session s = sessions.save(Session.builder().course(course)
            .startTime(LocalDateTime.now().minusMinutes(5)).endTime(LocalDateTime.now().plusMinutes(55))
            .geofenceLat(new BigDecimal("12.97")).geofenceLng(new BigDecimal("77.59")).geofenceRadiusM(100)
            .build());
        sessionId = s.getId();
    }

    private String url() {
        return "ws://localhost:" + port + "/api/ws-heatmap/websocket";
    }

    private StompSession connect(String token) throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        if (token != null) connectHeaders.add("Authorization", "Bearer " + token);
        return client.connectAsync(url(), new WebSocketHttpHeaders(), connectHeaders, new StompSessionHandlerAdapter() {})
            .get(5, TimeUnit.SECONDS);
    }

    private BlockingQueue<Map<?, ?>> subscribe(StompSession session) {
        BlockingQueue<Map<?, ?>> received = new LinkedBlockingQueue<>();
        session.subscribe("/topic/session/" + sessionId + "/heatmap", new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders headers) { return Map.class; }
            @Override public void handleFrame(StompHeaders headers, Object payload) { received.add((Map<?, ?>) payload); }
        });
        return received;
    }

    @Test
    void connectWithoutTokenIsRejected() {
        assertThatThrownBy(() -> {
            StompSession s = connect(null);
            // Some transports report the ERROR frame only after the CONNECTED attempt
            Thread.sleep(500);
            if (!s.isConnected()) throw new IllegalStateException("disconnected");
        }).isInstanceOfAny(ExecutionException.class, IllegalStateException.class, TimeoutException.class);
    }

    @Test
    void ownerReceivesLiveHeatmapUpdates() throws Exception {
        StompSession session = connect(ownerToken);
        BlockingQueue<Map<?, ?>> received = subscribe(session);
        Thread.sleep(300); // let the SUBSCRIBE register

        publisher.publish(sessionId);

        Map<?, ?> heatmap = received.poll(5, TimeUnit.SECONDS);
        assertThat(heatmap).isNotNull();
        assertThat(((Number) heatmap.get("sessionId")).longValue()).isEqualTo(sessionId);
    }

    @Test
    void anotherTeacherCannotSubscribe() throws Exception {
        StompSession session = connect(otherToken);
        BlockingQueue<Map<?, ?>> received = subscribe(session);
        Thread.sleep(300);

        publisher.publish(sessionId);

        assertThat(received.poll(1, TimeUnit.SECONDS)).isNull();
        assertThat(session.isConnected()).isFalse(); // server answers a denied SUBSCRIBE with ERROR + close
    }
}
