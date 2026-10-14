package com.liushao.user.controller;

import java.util.List;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = UserController.class, properties = {
        "how.auth.jwt.secret=test-only-signing-key-not-for-production", "logging.level.root=WARN"
})
@Import({UserService.class, PasswordConfig.class, com.liushao.user.service.AuthSessionService.class})
class UserControllerTest {
    @Autowired private MockMvc mvc;
    @Autowired private PasswordEncoder passwordEncoder;
    @MockBean private UserDao userDao;
    @MockBean private com.liushao.util.IdWorker idWorker;
    @MockBean private com.liushao.user.dao.AuthSessionDao sessions;
    @MockBean private com.liushao.user.dao.AuthRefreshTokenDao refreshTokens;
    @MockBean private com.liushao.auth.SessionVerifier verifier;
    @MockBean private AuthRateLimiter rateLimiter;

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
}