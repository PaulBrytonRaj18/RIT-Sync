package com.ritsync.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ritsync.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boots the full application (also proving it starts without allow-circular-references)
 * and exercises the authentication rules end to end.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthSecurityIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;

    private record Tokens(String email, String access, String refresh) {}

    private Tokens registerStudent() throws Exception {
        String email = "student-" + UUID.randomUUID() + "@ritsync.test";
        String body = """
            {"name":"Test Student","email":"%s","password":"Str0ngPassw0rd!",
             "role":"STUDENT","department":"CSE","rollNumber":"21CS999"}
            """.formatted(email);
        String res = mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        JsonNode data = json.readTree(res).get("data");
        return new Tokens(email, data.get("accessToken").asText(), data.get("refreshToken").asText());
    }

    @Test
    void protectedEndpointWithoutTokenReturns401Json() throws Exception {
        mvc.perform(get("/users/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void accessTokenWorks() throws Exception {
        Tokens t = registerStudent();
        mvc.perform(get("/users/me").header("Authorization", "Bearer " + t.access()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.email").value(t.email()));
    }

    @Test
    void refreshTokenIsRejectedAsBearerToken() throws Exception {
        Tokens t = registerStudent();
        mvc.perform(get("/users/me").header("Authorization", "Bearer " + t.refresh()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshEndpointIssuesNewWorkingTokens() throws Exception {
        Tokens t = registerStudent();
        String res = mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + t.refresh() + "\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String newAccess = json.readTree(res).at("/data/accessToken").asText();
        assertThat(newAccess).isNotBlank();
        mvc.perform(get("/users/me").header("Authorization", "Bearer " + newAccess))
            .andExpect(status().isOk());
    }

    @Test
    void refreshEndpointRejectsAccessToken() throws Exception {
        Tokens t = registerStudent();
        mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + t.access() + "\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void deactivatedUserLosesAccessImmediately() throws Exception {
        Tokens t = registerStudent();
        var user = users.findByEmail(t.email()).orElseThrow();
        user.setIsActive(false);
        users.save(user);

        mvc.perform(get("/users/me").header("Authorization", "Bearer " + t.access()))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + t.refresh() + "\"}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + t.email() + "\",\"password\":\"Str0ngPassw0rd!\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordReturns401() throws Exception {
        Tokens t = registerStudent();
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + t.email() + "\",\"password\":\"wrong-password\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void passwordEncodeDebugEndpointIsGone() throws Exception {
        mvc.perform(post("/auth/encode").contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"x\"}"))
            .andExpect(status().isUnauthorized());
    }
}
