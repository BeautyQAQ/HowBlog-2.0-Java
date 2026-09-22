package com.liushao.user.controller;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.HandlerMethod;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liushao.auth.CurrentUserContext;
import com.liushao.auth.JwtAuthenticationInterceptor;
import com.liushao.auth.JwtTokenService;
import com.liushao.auth.RequireAuthentication;
import com.liushao.auth.SessionVerifier;
import com.liushao.user.dao.UserDao;
import com.liushao.user.pojo.User;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:session-flow;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false", "how.auth.jwt.secret=test-only-signing-key-not-for-production",
        "how.auth.rate-limit.enabled=false", "how.auth.cleanup.enabled=false",
        "logging.level.root=WARN"
})
@AutoConfigureMockMvc
public class SessionFlowTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private UserDao users;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtAuthenticationInterceptor interceptor;
    @SpyBean private JwtTokenService tokens;
    @SpyBean private SessionVerifier verifier;
    private String userId;

    @BeforeEach
    void fixture() {
        jdbc.execute("CREATE ALIAS IF NOT EXISTS UTC_TIMESTAMP FOR 'com.liushao.user.controller.SessionFlowTest.utcTimestamp'");
        userId = UUID.randomUUID().toString();
        User user = new User();
        user.setId(userId);
        user.setMobile(userId);
        user.setPassword("test-password");
        users.saveAndFlush(user);
    }

    public static LocalDateTime utcTimestamp(int precision) {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    @Test
    void loginRefreshReplayAndDeviceIsolation() throws Exception {
        JsonNode first = login();
        JsonNode second = login();
        String oldAccess = first.path("token").asText();
        String oldRefresh = first.path("refreshToken").asText();
        assertAccess(oldAccess, 200);
        JsonNode next = refresh(oldRefresh);
        assertNotEquals(oldRefresh, next.path("refreshToken").asText());
        assertAccess(next.path("token").asText(), 200);
        mvc.perform(post("/user/refresh").header("Authorization", "Bearer expired")
                        .contentType(MediaType.APPLICATION_JSON).content(body(oldRefresh)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(20003));
        assertAccess(oldAccess, 401);
        assertAccess(next.path("token").asText(), 401);
        assertAccess(second.path("token").asText(), 200);
    }

    @Test
    void logoutWorksWithoutUsableAccessAndIsIdempotent() throws Exception {
        JsonNode login = login();
        String refreshToken = login.path("refreshToken").asText();
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post("/user/logout").header("Authorization", "Bearer expired")
                            .contentType(MediaType.APPLICATION_JSON).content(body(refreshToken)))
                    .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.flag").value(true));
        }
        assertAccess(login.path("token").asText(), 401);
        mvc.perform(post("/user/refresh").contentType(MediaType.APPLICATION_JSON).content(body(refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsExpiredMismatchedAndLegacyAccessTokens() throws Exception {
        JsonNode login = login();
        String sessionId = tokens.parseToken(login.path("token").asText()).getSessionId();
        assertAccess(tokens.issueToken("other-user", "other", sessionId, Instant.now().plusSeconds(120)), 401);
        assertAccess(tokens.issueToken(userId, userId), 401);
        jdbc.update("UPDATE tb_auth_session SET expires_at = ? WHERE id = ?", LocalDateTime.now(ZoneOffset.UTC).minusDays(1), sessionId);
        assertAccess(login.path("token").asText(), 401);
        mvc.perform(post("/user/refresh").contentType(MediaType.APPLICATION_JSON).content(body(login.path("refreshToken").asText())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingUserInvalidatesExistingAccess() throws Exception {
        JsonNode login = login();
        users.deleteById(userId);
        assertAccess(login.path("token").asText(), 401);
    }

    @Test
    void signingFailureRollsBackSessionAndPasswordUpgrade() throws Exception {
        doThrow(new IllegalStateException("signing unavailable")).when(tokens)
                .issueToken(anyString(), anyString(), anyString(), any(Instant.class));
        mvc.perform(post("/user/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("mobile", userId, "password", "test-password"))))
                .andExpect(status().isInternalServerError());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM tb_auth_session WHERE user_id = ?", Integer.class, userId));
        assertEquals("test-password", users.findById(userId).orElseThrow().getPassword());
    }

    @Test
    void signingFailureRollsBackRefreshConsumption() throws Exception {
        JsonNode login = login();
        String refreshToken = login.path("refreshToken").asText();
        doThrow(new IllegalStateException("signing unavailable")).when(tokens)
                .issueToken(anyString(), anyString(), anyString(), any(Instant.class));
        mvc.perform(post("/user/refresh").contentType(MediaType.APPLICATION_JSON).content(body(refreshToken)))
                .andExpect(status().isInternalServerError());
        reset(tokens);
        refresh(refreshToken);
        assertAccess(login.path("token").asText(), 200);
    }

    @Test
    void malformedOrUnknownCredentialsDoNotRevokeOtherSessions() throws Exception {
        JsonNode login = login();
        for (String path : new String[] {"/user/refresh", "/user/logout"}) {
            mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest());
            mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body("0".repeat(64))))
                    .andExpect(status().isUnauthorized());
        }
        assertAccess(login.path("token").asText(), 200);
    }

    @Test
    void databaseFailureReturnsSafeErrorAndNeverSetsIdentity() throws Exception {
        JsonNode login = login();
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("internal connection"))
                .when(verifier).isActive(anyString(), anyString());
        mvc.perform(post("/user/login").header("Authorization", "Bearer " + login.path("token").asText())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.message").value("服务暂时不可用，请稍后重试"));
        assertTrue(CurrentUserContext.get().isEmpty());
    }

    private JsonNode login() throws Exception {
        String response = mvc.perform(post("/user/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("mobile", userId, "password", "test-password"))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshExpiresIn").isNumber())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).path("data");
    }

    private JsonNode refresh(String refreshToken) throws Exception {
        String response = mvc.perform(post("/user/refresh").header("Authorization", "Bearer expired")
                        .contentType(MediaType.APPLICATION_JSON).content(body(refreshToken)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).path("data");
    }

    private String body(String refreshToken) throws Exception {
        return mapper.writeValueAsString(Map.of("refreshToken", refreshToken));
    }

    private void assertAccess(String token, int expected) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/protected");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        HandlerMethod handler = new HandlerMethod(new ProtectedEndpoint(), ProtectedEndpoint.class.getMethod("write"));
        try {
            assertEquals(expected == 200, interceptor.preHandle(request, response, handler));
            assertEquals(expected, response.getStatus());
        } finally {
            interceptor.afterCompletion(request, response, handler, null);
        }
    }

    public static class ProtectedEndpoint {
        @RequireAuthentication
        public void write() { }
    }
}