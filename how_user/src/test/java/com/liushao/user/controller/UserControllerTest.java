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

import static org.mockito.Mockito.verifyNoInteractions;
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