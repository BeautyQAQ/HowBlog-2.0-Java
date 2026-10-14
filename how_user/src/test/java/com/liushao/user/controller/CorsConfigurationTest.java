package com.liushao.user.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.liushao.user.config.PasswordConfig;
import com.liushao.user.dao.UserDao;
import com.liushao.user.service.AuthRateLimiter;
import com.liushao.user.service.UserService;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserController.class, properties = {
        "how.auth.jwt.secret=test-only-signing-key-not-for-production", "logging.level.root=WARN",
        "how.cors.allowed-origins=https://allowed.test, https://second.test"
})
@Import({UserService.class, com.liushao.user.service.UserFollowService.class, PasswordConfig.class,
        com.liushao.user.service.AuthSessionService.class})
class CorsConfigurationTest {
    @Autowired private MockMvc mvc;
    @MockBean private UserDao userDao;
    @MockBean private com.liushao.user.dao.AuthSessionDao sessions;
    @MockBean private com.liushao.user.dao.AuthRefreshTokenDao refreshTokens;
    @MockBean private com.liushao.auth.SessionVerifier verifier;
    @MockBean private AuthRateLimiter rateLimiter;
    @MockBean private com.liushao.user.dao.UserFollowDao userFollowDao;

    @Test
    void allowsPreflightOnlyFromConfiguredOrigins() throws Exception {
        mvc.perform(options("/user/login").header(HttpHeaders.ORIGIN, "https://allowed.test")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.PUT.name()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://allowed.test"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "PUT"));

        mvc.perform(options("/user/login").header(HttpHeaders.ORIGIN, "https://denied.test")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.PUT.name()))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void rejectsActualRequestsFromUnconfiguredOrigins() throws Exception {
        mvc.perform(post("/user/login").header(HttpHeaders.ORIGIN, "https://denied.test")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void trimsConfiguredOriginsAndKeepsRetryAfterExposed() throws Exception {
        mvc.perform(post("/user/login").header(HttpHeaders.ORIGIN, "https://second.test")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://second.test"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, containsString("Retry-After")));
    }
}
