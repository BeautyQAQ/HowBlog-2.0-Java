package com.liushao.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liushao.auth.JwtTokenService;
import com.liushao.user.dao.UserDao;
import com.liushao.user.pojo.LoginRequest;
import com.liushao.user.pojo.LoginResponse;
import com.liushao.user.pojo.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    private static final String MOBILE = "13800000000";
    private static final String PASSWORD = "correct-password";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private UserDao userDao;

    private PasswordEncoder passwordEncoder;
    private UserService userService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(4);
        JwtTokenService jwtTokenService = new JwtTokenService(
                "01234567890123456789012345678901",
                Duration.ofMinutes(30)
        );
        userService = new UserService(userDao, passwordEncoder, jwtTokenService);
    }

    @Test
    void loginUsesMobileOnlyUpgradesLegacyPasswordAndReturnsNoPassword() throws Exception {
        User user = userWithPassword(PASSWORD);
        when(userDao.findAllByMobile(MOBILE)).thenReturn(List.of(user));

        LoginResponse response = userService.login(loginRequest(PASSWORD));

        assertEquals("10001", response.getId());
        assertEquals(MOBILE, response.getMobile());
        assertEquals("Alice", response.getNickname());
        assertEquals("Bearer", response.getTokenType());
        assertEquals(1800, response.getExpiresIn());
        assertFalse(response.getToken().isBlank());
        assertTrue(passwordEncoder.matches(PASSWORD, user.getPassword()));
        assertFalse(objectMapper.writeValueAsString(response).contains("password"));
        verify(userDao).findAllByMobile(MOBILE);
        verify(userDao).save(user);
    }

    @Test
    void loginWithBcryptPasswordDoesNotWriteTheUserAgain() {
        User user = userWithPassword(passwordEncoder.encode(PASSWORD));
        when(userDao.findAllByMobile(MOBILE)).thenReturn(List.of(user));

        LoginResponse response = userService.login(loginRequest(PASSWORD));

        assertEquals("10001", response.getId());
        verify(userDao, never()).save(user);
    }

    @Test
    void loginRejectsAmbiguousOrInvalidCredentials() {
        when(userDao.findAllByMobile(MOBILE)).thenReturn(List.of(
                userWithPassword(PASSWORD),
                userWithPassword(PASSWORD)
        ));

        assertNull(userService.login(loginRequest(PASSWORD)));
        verify(userDao, never()).save(org.mockito.ArgumentMatchers.any(User.class));
    }

    private LoginRequest loginRequest(String password) {
        LoginRequest request = new LoginRequest();
        request.setMobile(MOBILE);
        request.setPassword(password);
        return request;
    }

    private User userWithPassword(String password) {
        User user = new User();
        user.setId("10001");
        user.setMobile(MOBILE);
        user.setNickname("Alice");
        user.setAvatar("https://example.test/avatar.png");
        user.setPassword(password);
        return user;
    }
}