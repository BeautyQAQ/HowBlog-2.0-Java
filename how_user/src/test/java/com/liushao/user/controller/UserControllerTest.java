package com.liushao.user.controller;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.liushao.user.config.PasswordConfig;
import com.liushao.user.dao.UserDao;
import com.liushao.user.pojo.User;
import com.liushao.user.service.UserService;
import com.liushao.user.service.AuthRateLimiter;
import com.liushao.user.service.AuthRateLimiter.Scope;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = UserController.class, properties = {
        "how.auth.jwt.secret=test-only-signing-key-not-for-production", "logging.level.root=WARN"
})
@Import({UserService.class, com.liushao.user.service.UserFollowService.class, PasswordConfig.class,
        com.liushao.user.service.AuthSessionService.class})
class UserControllerTest {
    @Autowired private MockMvc mvc;
    @Autowired private PasswordEncoder passwordEncoder;
    @MockBean private UserDao userDao;
    @MockBean private com.liushao.util.IdWorker idWorker;
    @MockBean private com.liushao.user.dao.AuthSessionDao sessions;
    @MockBean private com.liushao.user.dao.AuthRefreshTokenDao refreshTokens;
    @MockBean private com.liushao.auth.SessionVerifier verifier;
    @MockBean private AuthRateLimiter rateLimiter;
    @MockBean private com.liushao.user.dao.UserFollowDao userFollowDao;
    @Autowired private com.liushao.auth.JwtTokenService tokens;

    @ParameterizedTest
    @CsvSource({"/user/login, LOGIN_IP", "/user/register, REGISTER_IP", "/user/password, PASSWORD_IP",
            "/user/refresh, REFRESH_IP", "/user/logout, LOGOUT_IP"})
    void rateLimitRunsBeforeAuthenticationParsingAndBusiness(String path, Scope scope) throws Exception {
        doThrow(new AuthRateLimiter.Rejected(7)).when(rateLimiter).check(scope, "127.0.0.1");
        mvc.perform(post(path).header("Authorization", "Bearer invalid")
                        .header("Origin", "https://example.test").header("X-Forwarded-For", "198.51.100.1")
                        .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "7"))
                .andExpect(header().string("Access-Control-Expose-Headers", containsString("Retry-After")))
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.code").value(20001))
                .andExpect(jsonPath("$.flag").value(false));
        verify(rateLimiter).check(scope, "127.0.0.1");
        verifyNoInteractions(userDao, sessions, refreshTokens, verifier);
    }

    @Test
    void accountLimitRunsBeforePasswordLookup() throws Exception {
        doThrow(new AuthRateLimiter.Rejected(60)).when(rateLimiter).check(Scope.LOGIN_ACCOUNT, "test-user");
        mvc.perform(post("/user/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mobile\":\"test-user\",\"password\":\"test-password\"}"))
                .andExpect(status().isTooManyRequests());
        verifyNoInteractions(userDao, sessions, refreshTokens);
    }

    @Test
    void unavailableLimiterFailsClosed() throws Exception {
        doThrow(new AuthRateLimiter.Rejected(0)).when(rateLimiter).check(Scope.REFRESH_IP, "127.0.0.1");
        mvc.perform(post("/user/refresh").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isServiceUnavailable()).andExpect(header().string("Retry-After", "5"))
                .andExpect(jsonPath("$.message").value("服务暂时不可用，请稍后重试"));
        verifyNoInteractions(userDao, sessions, refreshTokens);
    }

    @Test
    void allowsAnyOriginByDefaultAndKeepsRetryAfterExposed() throws Exception {
        mvc.perform(post("/user/login").header(HttpHeaders.ORIGIN, "https://example.test")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, containsString("Retry-After")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"mobile\":\"test-user\"}", "{\"mobile\":\" \",\"password\":\"test-password\"}"})
    void preservesGenericFailureForMissingCredentials(String body) throws Exception {
        mvc.perform(post("/user/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(20002))
                .andExpect(jsonPath("$.message").value("手机号或密码错误"));
        verifyNoInteractions(userDao);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "null", ""})
    void wrapsInvalidJsonAndMissingBody(String body) throws Exception {
        mvc.perform(post("/user/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(20001))
                .andExpect(jsonPath("$.message").value("请求格式或参数不正确"));
        verifyNoInteractions(userDao);
    }

    @Test
    void returnsTokenWithoutPassword() throws Exception {
        User user = new User();
        user.setId("author");
        user.setMobile("test-user");
        user.setPassword(passwordEncoder.encode("test-password"));
        when(userDao.findAllByMobile("test-user")).thenReturn(List.of(user));
        when(userDao.existsById("author")).thenReturn(true);
        mvc.perform(post("/user/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mobile\":\"test-user\",\"password\":\"test-password\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.flag").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    void hidesUnexpectedLoginFailure() throws Exception {
        when(userDao.findAllByMobile("test-user")).thenThrow(new IllegalStateException("internal database detail"));
        mvc.perform(post("/user/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mobile\":\"test-user\",\"password\":\"test-password\"}"))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value(20001))
                .andExpect(jsonPath("$.message").value("服务暂时不可用，请稍后重试"));
    }

    @Test
    void registerCreatesUserWithBcryptPassword() throws Exception {
        when(idWorker.nextId()).thenReturn(20261014001L);
        when(userDao.findAllByMobile("13900000000")).thenReturn(List.of());
        mvc.perform(post("/user/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mobile\":\"13900000000\",\"password\":\"password-123\",\"nickname\":\"Bob\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.flag").value(true))
                .andExpect(jsonPath("$.code").value(20000)).andExpect(jsonPath("$.message").value("注册成功"))
                .andExpect(jsonPath("$.data").value(nullValue()));
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userDao).save(saved.capture());
        assertEquals("20261014001", saved.getValue().getId());
        assertTrue(saved.getValue().getPassword().startsWith("$2"));
        assertFalse(saved.getValue().getPassword().contains("password-123"));
    }

    @Test
    void registerRejectsDuplicateMobile() throws Exception {
        User existing = new User();
        existing.setId("10001");
        existing.setMobile("13900000000");
        when(userDao.findAllByMobile("13900000000")).thenReturn(List.of(existing));
        mvc.perform(post("/user/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mobile\":\"13900000000\",\"password\":\"password-123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.flag").value(false))
                .andExpect(jsonPath("$.code").value(20005)).andExpect(jsonPath("$.message").value("手机号已注册"));
        verify(userDao, never()).save(any(User.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"mobile\":\"12345\",\"password\":\"password-123\"}",
            "{\"mobile\":\"13900000000\",\"password\":\"short\"}",
            "{\"mobile\":\"13900000000\",\"password\":\"        \"}",
            "{\"mobile\":\"13900000000\"}"
    })
    void registerRejectsInvalidBodyWithBadRequest(String body) throws Exception {
        mvc.perform(post("/user/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(20001))
                .andExpect(jsonPath("$.message").value("请求格式或参数不正确"));
        verifyNoInteractions(userDao);
    }

    @Test
    void databaseUniqueConstraintConflictReturnsDuplicateCode() throws Exception {
        when(userDao.findAllByMobile("13900000000")).thenReturn(List.of());
        when(userDao.save(any(User.class))).thenThrow(new DataIntegrityViolationException(
                "Duplicate entry '13900000000' for key 'tb_user.uk_tb_user_mobile'"));
        mvc.perform(post("/user/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mobile\":\"13900000000\",\"password\":\"password-123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.flag").value(false))
                .andExpect(jsonPath("$.code").value(20005)).andExpect(jsonPath("$.message").value("手机号已注册"));
    }

    @Test
    void unrelatedIntegrityFailureStaysHidden() throws Exception {
        when(userDao.findAllByMobile("13900000000")).thenReturn(List.of());
        when(userDao.save(any(User.class))).thenThrow(new DataIntegrityViolationException(
                "some other constraint violation"));
        mvc.perform(post("/user/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mobile\":\"13900000000\",\"password\":\"password-123\"}"))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value(20001))
                .andExpect(jsonPath("$.message").value("服务暂时不可用，请稍后重试"));
    }

    @Test
    void followEndpointsRequireAuthentication() throws Exception {
        mvc.perform(put("/user/follow/bob")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(20003));
        mvc.perform(delete("/user/follow/bob")).andExpect(status().isUnauthorized());
        verifyNoInteractions(userFollowDao);
    }

    @Test
    void followMapsSuccessSelfDuplicateAndMissing() throws Exception {
        when(userDao.findByIdForUpdate("bob")).thenReturn(Optional.of(new User()));
        when(userFollowDao.insertIfAbsent("alice", "bob")).thenReturn(1);
        mvc.perform(put("/user/follow/bob").header("Authorization", authorization()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(20000))
                .andExpect(jsonPath("$.message").value("关注成功"));
        verify(userDao).incrementFans("bob");
        verify(userDao).incrementFollow("alice");

        mvc.perform(put("/user/follow/alice").header("Authorization", authorization()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(20001))
                .andExpect(jsonPath("$.message").value("不能关注自己"));

        when(userFollowDao.insertIfAbsent("alice", "bob")).thenReturn(0);
        mvc.perform(put("/user/follow/bob").header("Authorization", authorization()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(20005))
                .andExpect(jsonPath("$.message").value("已关注"));

        when(userDao.findByIdForUpdate("ghost")).thenReturn(Optional.empty());
        mvc.perform(put("/user/follow/ghost").header("Authorization", authorization()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(20001));
    }

    @Test
    void unfollowMapsSuccessNotFollowingAndMissing() throws Exception {
        when(userDao.findByIdForUpdate("bob")).thenReturn(Optional.of(new User()));
        when(userFollowDao.deleteRelation("alice", "bob")).thenReturn(1);
        mvc.perform(delete("/user/follow/bob").header("Authorization", authorization()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(20000))
                .andExpect(jsonPath("$.message").value("已取消关注"));
        verify(userDao).decrementFans("bob");
        verify(userDao).decrementFollow("alice");

        when(userFollowDao.deleteRelation("alice", "bob")).thenReturn(0);
        mvc.perform(delete("/user/follow/bob").header("Authorization", authorization()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(20005))
                .andExpect(jsonPath("$.message").value("未关注"));

        when(userDao.findByIdForUpdate("ghost")).thenReturn(Optional.empty());
        mvc.perform(delete("/user/follow/ghost").header("Authorization", authorization()))
                .andExpect(status().isNotFound());
    }

    @Test
    void listsFansAndFollowsAndRejectsBadPaging() throws Exception {
        when(userDao.existsById("bob")).thenReturn(true);
        when(userDao.findFollowers("bob", PageRequest.of(0, 10))).thenReturn(new PageImpl<>(List.of(follower())));
        mvc.perform(get("/user/bob/fans/1/10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.rows[0].id").value("alice"))
                .andExpect(jsonPath("$.data.rows[0].mobile").doesNotExist())
                .andExpect(jsonPath("$.data.rows[0].password").doesNotExist());

        mvc.perform(get("/user/bob/fans/0/10")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(20001));

        when(userDao.existsById("ghost")).thenReturn(false);
        mvc.perform(get("/user/ghost/follows/1/10")).andExpect(status().isNotFound());
    }

    private User follower() {
        User user = new User();
        user.setId("alice");
        user.setNickname("Alice");
        user.setMobile("13800000000");
        user.setPassword("$2a$10$secret");
        return user;
    }

    private String authorization() {
        when(verifier.isActive("a".repeat(64), "alice")).thenReturn(true);
        return "Bearer " + tokens.issueToken("alice", "test-user", "a".repeat(64),
                java.time.Instant.now().plusSeconds(3600));
    }
}