package com.liushao.user.controller;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.liushao.user.config.PasswordConfig;
import com.liushao.user.dao.UserDao;
import com.liushao.user.pojo.User;
import com.liushao.user.service.UserService;
import com.liushao.user.service.AuthRateLimiter;
import com.liushao.user.service.AuthRateLimiter.Scope;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.doThrow;
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
    @MockBean private com.liushao.user.dao.AuthSessionDao sessions;
    @MockBean private com.liushao.user.dao.AuthRefreshTokenDao refreshTokens;
    @MockBean private com.liushao.auth.SessionVerifier verifier;
    @MockBean private AuthRateLimiter rateLimiter;

    @ParameterizedTest
    @ValueSource(strings = {"login", "refresh", "logout"})
    void rateLimitRunsBeforeAuthenticationParsingAndBusiness(String action) throws Exception {
        Scope scope = Scope.valueOf(action.toUpperCase(java.util.Locale.ROOT) + "_IP");
        doThrow(new AuthRateLimiter.Rejected(7)).when(rateLimiter).check(scope, "127.0.0.1");
        mvc.perform(post("/user/" + action).header("Authorization", "Bearer invalid")
                        .header("Origin", "https://example.test").header("X-Forwarded-For", "198.51.100.1")
                        .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "7"))
                .andExpect(header().string("Access-Control-Expose-Headers", "Retry-After"))
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
}