package com.liushao.user.controller;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liushao.user.dao.UserDao;
import com.liushao.user.pojo.User;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:user-password;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false", "how.auth.jwt.secret=test-only-signing-key-not-for-production",
        "how.auth.rate-limit.enabled=false", "how.auth.cleanup.enabled=false",
        "logging.level.root=WARN"
})
@AutoConfigureMockMvc
public class UserPasswordTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private UserDao users;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbc;
    private String userId;

    @BeforeEach
    void fixture() {
        jdbc.execute("CREATE ALIAS IF NOT EXISTS UTC_TIMESTAMP FOR 'com.liushao.user.controller.UserPasswordTest.utcTimestamp'");
        userId = UUID.randomUUID().toString();
        User user = new User();
        user.setId(userId);
        user.setMobile(userId);
        user.setPassword(passwordEncoder.encode("test-password"));
        users.saveAndFlush(user);
    }

    public static LocalDateTime utcTimestamp(int precision) {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    @Test
    void passwordChangeRequiresAuthentication() throws Exception {
        mvc.perform(post("/user/password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"test-password\",\"newPassword\":\"new-password-456\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongOldPasswordIsRejectedAndKeepsSessions() throws Exception {
        String token = login("test-password").path("token").asText();
        mvc.perform(post("/user/password").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"wrong-password-1\",\"newPassword\":\"new-password-456\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.flag").value(false))
                .andExpect(jsonPath("$.code").value(20002)).andExpect(jsonPath("$.message").value("旧密码不正确"));
        mvc.perform(get("/user/profile").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        assertTrue(passwordEncoder.matches("test-password", users.findById(userId).orElseThrow().getPassword()));
    }

    @Test
    void invalidNewPasswordIsRejected() throws Exception {
        String token = login("test-password").path("token").asText();
        for (String body : new String[]{
                "{\"oldPassword\":\"test-password\",\"newPassword\":\"short\"}",
                "{\"oldPassword\":\"test-password\",\"newPassword\":\"        \"}",
                "{\"oldPassword\":\"\",\"newPassword\":\"new-password-456\"}",
                "{}"
        }) {
            mvc.perform(post("/user/password").header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(20001));
        }
        assertTrue(passwordEncoder.matches("test-password", users.findById(userId).orElseThrow().getPassword()));
    }

    @Test
    void successRevokesOtherSessionsAndKeepsCurrentOne() throws Exception {
        JsonNode sessionA = login("test-password");
        JsonNode sessionB = login("test-password");
        mvc.perform(post("/user/password").header("Authorization", "Bearer " + sessionA.path("token").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"test-password\",\"newPassword\":\"new-password-456\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.flag").value(true))
                .andExpect(jsonPath("$.code").value(20000)).andExpect(jsonPath("$.message").value("修改成功"));

        mvc.perform(get("/user/profile").header("Authorization", "Bearer " + sessionB.path("token").asText()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/user/profile").header("Authorization", "Bearer " + sessionA.path("token").asText()))
                .andExpect(status().isOk());
        mvc.perform(post("/user/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("refreshToken", sessionB.path("refreshToken").asText()))))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/user/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("refreshToken", sessionA.path("refreshToken").asText()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(20000));

        mvc.perform(post("/user/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("mobile", userId, "password", "test-password"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(20002));
        assertNotNull(login("new-password-456").path("token").asText());
        String stored = users.findById(userId).orElseThrow().getPassword();
        assertTrue(stored.startsWith("$2"));
        assertTrue(passwordEncoder.matches("new-password-456", stored));
        assertFalse(passwordEncoder.matches("test-password", stored));
    }

    private JsonNode login(String password) throws Exception {
        String response = mvc.perform(post("/user/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("mobile", userId, "password", password))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.flag").value(true))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).path("data");
    }
}
