package com.liushao.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liushao.auth.JwtTokenService;
import com.liushao.user.dao.UserDao;
import com.liushao.user.pojo.ChangePasswordRequest;
import com.liushao.user.pojo.LoginRequest;
import com.liushao.user.pojo.LoginResponse;
import com.liushao.user.pojo.ProfileResponse;
import com.liushao.user.pojo.ProfileUpdateRequest;
import com.liushao.user.pojo.RegisterRequest;
import com.liushao.user.pojo.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    private static final String MOBILE = "13800000000";
    private static final String PASSWORD = "correct-password";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private UserDao userDao;

    @Mock
    private com.liushao.util.IdWorker idWorker;

    @Mock
    private com.liushao.user.dao.AuthSessionDao sessionDao;

    private PasswordEncoder passwordEncoder;
    private UserService userService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(4);
        JwtTokenService jwtTokenService = new JwtTokenService(
                "01234567890123456789012345678901",
                Duration.ofMinutes(30)
        );
        AuthSessionService sessions = new AuthSessionService(sessionDao,
            org.mockito.Mockito.mock(com.liushao.user.dao.AuthRefreshTokenDao.class), userDao);
        userService = new UserService(userDao, passwordEncoder, jwtTokenService, sessions, idWorker);
    }

    @Test
    void loginUsesMobileOnlyUpgradesLegacyPasswordAndReturnsNoPassword() throws Exception {
        when(userDao.existsById("10001")).thenReturn(true);
        User user = userWithPassword(PASSWORD);
        when(userDao.findAllByMobile(MOBILE)).thenReturn(List.of(user));

        LoginResponse response = userService.login(loginRequest(PASSWORD));

        assertEquals("10001", response.getId());
        assertEquals(MOBILE, response.getMobile());
        assertEquals("Alice", response.getNickname());
        assertEquals("Bearer", response.getTokenType());
        assertTrue(response.getExpiresIn() > 0 && response.getExpiresIn() <= 1800);
        assertTrue(response.getRefreshToken().matches("[0-9a-f]{64}"));
        assertFalse(response.getToken().isBlank());
        assertTrue(passwordEncoder.matches(PASSWORD, user.getPassword()));
        assertFalse(objectMapper.writeValueAsString(response).contains("password"));
        verify(userDao).findAllByMobile(MOBILE);
        verify(userDao).save(user);
    }

    @Test
    void loginWithBcryptPasswordDoesNotWriteTheUserAgain() {
        when(userDao.existsById("10001")).thenReturn(true);
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

    @Test
    void registerStoresBcryptPasswordAndGeneratedId() {
        when(idWorker.nextId()).thenReturn(42L);
        when(userDao.findAllByMobile(MOBILE)).thenReturn(List.of());

        assertEquals(UserService.RegisterResult.SUCCESS, userService.register(registerRequest(MOBILE, PASSWORD, "  Alice  ")));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userDao).save(saved.capture());
        User user = saved.getValue();
        assertEquals("42", user.getId());
        assertEquals(MOBILE, user.getMobile());
        assertEquals("Alice", user.getNickname());
        assertTrue(user.getPassword().startsWith("$2"));
        assertTrue(passwordEncoder.matches(PASSWORD, user.getPassword()));
        assertNotNull(user.getRegdate());
        assertEquals(0, user.getFanscount());
        assertEquals(0, user.getFollowcount());
        assertEquals(0L, user.getOnline());
    }

    @Test
    void registerRejectsExistingMobileIncludingLegacyDuplicates() {
        when(userDao.findAllByMobile(MOBILE)).thenReturn(List.of(userWithPassword(PASSWORD)));
        assertEquals(UserService.RegisterResult.DUPLICATE,
                userService.register(registerRequest(MOBILE, PASSWORD, null)));

        when(userDao.findAllByMobile(MOBILE)).thenReturn(List.of(
                userWithPassword(PASSWORD),
                userWithPassword(PASSWORD)
        ));
        assertEquals(UserService.RegisterResult.DUPLICATE,
                userService.register(registerRequest(MOBILE, PASSWORD, null)));
        verify(userDao, never()).save(org.mockito.ArgumentMatchers.any(User.class));
    }

    @Test
    void registerRejectsInvalidInputWithoutWriting() {
        assertEquals(UserService.RegisterResult.INVALID, userService.register(null));
        assertEquals(UserService.RegisterResult.INVALID, userService.register(registerRequest("", PASSWORD, null)));
        assertEquals(UserService.RegisterResult.INVALID, userService.register(registerRequest("12345", PASSWORD, null)));
        assertEquals(UserService.RegisterResult.INVALID, userService.register(registerRequest("213800000000", PASSWORD, null)));
        assertEquals(UserService.RegisterResult.INVALID, userService.register(registerRequest(MOBILE, "short", null)));
        assertEquals(UserService.RegisterResult.INVALID, userService.register(registerRequest(MOBILE, "x".repeat(65), null)));
        assertEquals(UserService.RegisterResult.INVALID, userService.register(registerRequest(MOBILE, "        ", null)));
        verify(userDao, never()).findAllByMobile(org.mockito.ArgumentMatchers.anyString());
        verify(userDao, never()).save(org.mockito.ArgumentMatchers.any(User.class));
    }

    @Test
    void profileReturnsNullForMissingUserAndMapsFields() {
        assertNull(userService.profile("missing"));

        User user = userWithPassword(PASSWORD);
        user.setEmail("alice@example.test");
        when(userDao.findById("10001")).thenReturn(java.util.Optional.of(user));

        ProfileResponse profile = userService.profile("10001");

        assertEquals("10001", profile.getId());
        assertEquals(MOBILE, profile.getMobile());
        assertEquals("Alice", profile.getNickname());
        assertEquals("alice@example.test", profile.getEmail());
    }

    @Test
    void updateProfileAppliesOnlyProvidedFieldsAndStrips() {
        User user = userWithPassword(PASSWORD);
        user.setAvatar("https://example.test/old.png");
        when(userDao.findById("10001")).thenReturn(java.util.Optional.of(user));
        ProfileUpdateRequest request = new ProfileUpdateRequest();
        request.setNickname("  Bob  ");
        request.setEmail("bob@example.test");

        ProfileResponse updated = userService.updateProfile("10001", request);

        assertEquals("Bob", updated.getNickname());
        assertEquals("bob@example.test", updated.getEmail());
        assertEquals("https://example.test/old.png", updated.getAvatar());
        assertEquals(MOBILE, updated.getMobile());
        assertNotNull(user.getUpdatedate());
        verify(userDao).save(user);
    }

    @Test
    void updateProfileReturnsNullForMissingUser() {
        when(userDao.findById("missing")).thenReturn(java.util.Optional.empty());

        assertNull(userService.updateProfile("missing", new ProfileUpdateRequest()));

        verify(userDao, never()).save(org.mockito.ArgumentMatchers.any(User.class));
    }

    @Test
    void changePasswordStoresNewHashAndRevokesOtherSessions() {
        when(userDao.findById("10001")).thenReturn(java.util.Optional.of(userWithPassword(passwordEncoder.encode(PASSWORD))));
        when(sessionDao.revokeActiveExcept(eq("10001"), eq("session-1"), any(java.time.LocalDateTime.class))).thenReturn(2);
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setOldPassword(PASSWORD);
        request.setNewPassword("new-password-456");

        assertEquals(UserService.PasswordChangeResult.SUCCESS, userService.changePassword("10001", "session-1", request));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userDao).save(saved.capture());
        assertTrue(passwordEncoder.matches("new-password-456", saved.getValue().getPassword()));
        verify(sessionDao).revokeActiveExcept(eq("10001"), eq("session-1"), any(java.time.LocalDateTime.class));
    }

    @Test
    void changePasswordRejectsWrongOldPasswordWithoutWriting() {
        when(userDao.findById("10001")).thenReturn(java.util.Optional.of(userWithPassword(passwordEncoder.encode(PASSWORD))));
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setOldPassword("wrong-password-1");
        request.setNewPassword("new-password-456");

        assertEquals(UserService.PasswordChangeResult.WRONG_PASSWORD,
                userService.changePassword("10001", "session-1", request));

        verify(userDao, never()).save(org.mockito.ArgumentMatchers.any(User.class));
        verifyNoInteractions(sessionDao);
    }

    @Test
    void changePasswordRejectsInvalidInputAndMissingUser() {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setOldPassword(PASSWORD);
        request.setNewPassword("short");
        assertEquals(UserService.PasswordChangeResult.INVALID, userService.changePassword("10001", "session-1", request));
        assertEquals(UserService.PasswordChangeResult.INVALID, userService.changePassword("10001", "session-1", null));
        request.setNewPassword("x".repeat(65));
        assertEquals(UserService.PasswordChangeResult.INVALID, userService.changePassword("10001", "session-1", request));
        request.setNewPassword("new-password-456");
        request.setOldPassword("   ");
        assertEquals(UserService.PasswordChangeResult.INVALID, userService.changePassword("10001", "session-1", request));

        when(userDao.findById("missing")).thenReturn(java.util.Optional.empty());
        request.setOldPassword(PASSWORD);
        assertNull(userService.changePassword("missing", "session-1", request));

        verify(userDao, never()).save(org.mockito.ArgumentMatchers.any(User.class));
        verifyNoInteractions(sessionDao);
    }

    private RegisterRequest registerRequest(String mobile, String password, String nickname) {
        RegisterRequest request = new RegisterRequest();
        request.setMobile(mobile);
        request.setPassword(password);
        request.setNickname(nickname);
        return request;
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