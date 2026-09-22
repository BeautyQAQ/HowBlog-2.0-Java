package com.liushao.user.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liushao.user.dao.AuthSessionDao;
import com.liushao.user.dao.AuthRefreshTokenDao;
import com.liushao.user.dao.UserDao;
import com.liushao.user.pojo.User;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

@DataJpaTest(showSql = false, properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "how.auth.jwt.secret=test-only-signing-key-not-for-production",
        "logging.level.root=WARN"
})
@Import(AuthSessionService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AuthSessionServiceTest {
    @MockBean private ObjectMapper objectMapper;
    @Autowired private AuthSessionService service;
    @Autowired private AuthSessionDao sessions;
    @SpyBean private AuthRefreshTokenDao refreshTokens;
    @Autowired private UserDao users;
    @Autowired private JdbcTemplate jdbc;
    private String userId;

    @BeforeEach
    void createUser() {
        User user = new User();
        userId = "session-test-" + UUID.randomUUID();
        user.setId(userId);
        users.saveAndFlush(user);
    }

    @Test
    void storesOnlyHashAndKeepsAbsoluteExpiryDuringRotation() throws Exception {
        AuthSessionService.Grant original = service.create(userId);
        assertTrue(original.getRefreshToken().matches("[0-9a-f]{64}"));
        byte[] stored = jdbc.queryForObject("SELECT token_hash FROM tb_auth_refresh_token WHERE session_id = ?",
                byte[].class, original.getSessionId());
        assertArrayEquals(MessageDigest.getInstance("SHA-256").digest(original.getRefreshToken()
                .getBytes(StandardCharsets.US_ASCII)), stored);
        assertFalse(original.toString().contains(original.getRefreshToken()));
        assertEquals(original.getExpiresAt(), jdbc.queryForObject(
            "SELECT expires_at FROM tb_auth_session WHERE id = ?", java.time.LocalDateTime.class,
            original.getSessionId()));
        AuthSessionService.Grant rotated = service.rotate(original.getRefreshToken()).orElseThrow();
        assertEquals(original.getSessionId(), rotated.getSessionId());
        assertEquals(original.getExpiresAt(), rotated.getExpiresAt());
        assertNotEquals(original.getRefreshToken(), rotated.getRefreshToken());
    }

    @Test
    void replayCommitsRevocationWithoutAffectingOtherSessions() {
        AuthSessionService.Grant first = service.create(userId);
        AuthSessionService.Grant independent = service.create(userId);
        AuthSessionService.Grant rotated = service.rotate(first.getRefreshToken()).orElseThrow();
        assertTrue(service.rotate(first.getRefreshToken()).isEmpty());
        assertNotNull(sessions.findById(first.getSessionId()).orElseThrow().getRevokedAt());
        assertTrue(service.rotate(rotated.getRefreshToken()).isEmpty());
        assertTrue(service.rotate(independent.getRefreshToken()).isPresent());
    }

    @Test
    void logoutIsIdempotentAndBlocksFurtherRotation() {
        AuthSessionService.Grant grant = service.create(userId);
        assertTrue(service.revoke(grant.getRefreshToken()));
        assertTrue(service.revoke(grant.getRefreshToken()));
        assertTrue(service.rotate(grant.getRefreshToken()).isEmpty());
    }

    @Test
    void rejectsMalformedAndUnknownTokensWithoutRevokingSessions() {
        AuthSessionService.Grant grant = service.create(userId);
        assertTrue(service.rotate(null).isEmpty());
        for (String value : List.of("", "bad-token", "a".repeat(65), "0".repeat(64))) {
            assertTrue(service.rotate(value).isEmpty());
            assertFalse(service.revoke(value));
        }
        assertNull(sessions.findById(grant.getSessionId()).orElseThrow().getRevokedAt());
        assertTrue(service.rotate(grant.getRefreshToken()).isPresent());
    }

    @Test
    void rejectsExpiredSession() {
        AuthSessionService.Grant grant = service.create(userId);
        jdbc.update("UPDATE tb_auth_session SET expires_at = TIMESTAMP '2000-01-01 00:00:00' WHERE id = ?",
                grant.getSessionId());
        assertTrue(service.rotate(grant.getRefreshToken()).isEmpty());
    }

    @Test
    void rejectsMissingUserAndRevokesOrphanSession() {
        assertThrows(IllegalArgumentException.class, () -> service.create("missing-" + UUID.randomUUID()));
        AuthSessionService.Grant grant = service.create(userId);
        users.deleteById(userId);
        assertTrue(service.rotate(grant.getRefreshToken()).isEmpty());
        assertNotNull(sessions.findById(grant.getSessionId()).orElseThrow().getRevokedAt());
    }

    @Test
    void replacementWriteFailureRollsBackConsumedMarker() {
        AuthSessionService.Grant grant = service.create(userId);
        doThrow(new IllegalStateException("simulated write failure"))
            .when(refreshTokens).saveAndFlush(any());
        assertThrows(IllegalStateException.class, () -> service.rotate(grant.getRefreshToken()));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM tb_auth_refresh_token WHERE session_id = ? AND consumed_at IS NOT NULL",
                Integer.class, grant.getSessionId()));
            reset(refreshTokens);
        assertTrue(service.rotate(grant.getRefreshToken()).isPresent());
    }

    @Test
    void concurrentRefreshHasOneWinnerAndCommitsReplayRevocation() throws Exception {
        AuthSessionService.Grant grant = service.create(userId);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return service.rotate(grant.getRefreshToken()).isPresent();
            });
            Future<Boolean> second = executor.submit(() -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return service.rotate(grant.getRefreshToken()).isPresent();
            });
            start.countDown();
            assertNotEquals(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
            assertNotNull(sessions.findById(grant.getSessionId()).orElseThrow().getRevokedAt());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(20, TimeUnit.SECONDS));
        }
    }

    @Test
    void concurrentLogoutAndRefreshCannotRestoreSession() throws Exception {
        AuthSessionService.Grant grant = service.create(userId);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> refresh = executor.submit(() -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return service.rotate(grant.getRefreshToken());
            });
            Future<Boolean> logout = executor.submit(() -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return service.revoke(grant.getRefreshToken());
            });
            start.countDown();
            refresh.get(20, TimeUnit.SECONDS);
            assertTrue(logout.get(20, TimeUnit.SECONDS));
            assertNotNull(sessions.findById(grant.getSessionId()).orElseThrow().getRevokedAt());
            assertTrue(service.rotate(grant.getRefreshToken()).isEmpty());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(20, TimeUnit.SECONDS));
        }
    }
}