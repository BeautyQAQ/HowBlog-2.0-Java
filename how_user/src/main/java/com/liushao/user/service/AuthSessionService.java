package com.liushao.user.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.liushao.user.dao.AuthRefreshTokenDao;
import com.liushao.user.dao.AuthSessionDao;
import com.liushao.user.dao.UserDao;
import com.liushao.user.pojo.AuthRefreshToken;
import com.liushao.user.pojo.AuthSession;

@Service
public class AuthSessionService {
    private final AuthSessionDao sessions;
    private final AuthRefreshTokenDao refreshTokens;
    private final UserDao users;
    private final SecureRandom random = new SecureRandom();

    public AuthSessionService(AuthSessionDao sessions, AuthRefreshTokenDao refreshTokens, UserDao users) {
        this.sessions = sessions;
        this.refreshTokens = refreshTokens;
        this.users = users;
    }

    @Transactional(timeout = 15)
    public Grant create(String userId) {
        if (userId == null || userId.isBlank() || !users.existsById(userId)) {
            throw new IllegalArgumentException("Session user does not exist");
        }
        LocalDateTime now = now();
        AuthSession session = new AuthSession(randomValue(), userId, now, now.plusDays(7));
        sessions.saveAndFlush(session);
        return issueRefreshToken(session, now);
    }

    @Transactional(timeout = 15)
    public Optional<Grant> rotate(String refreshToken) {
        Optional<AuthSession> found = lockSession(refreshToken);
        if (found.isEmpty()) return Optional.empty();
        AuthSession session = found.get();
        LocalDateTime now = now();
        if (!session.isActive(now)) return Optional.empty();
        AuthRefreshToken stored = refreshTokens.lockByHash(hash(refreshToken)).orElseThrow();
        if (stored.isConsumed() || !users.existsById(session.getUserId())) {
            session.revoke(now);
            return Optional.empty();
        }
        stored.consume(now);
        refreshTokens.flush();
        return Optional.of(issueRefreshToken(session, now));
    }

    @Transactional(timeout = 15)
    public boolean revoke(String refreshToken) {
        Optional<AuthSession> found = lockSession(refreshToken);
        if (found.isEmpty()) return false;
        found.get().revoke(now());
        return true;
    }

    private Optional<AuthSession> lockSession(String refreshToken) {
        if (refreshToken == null || !refreshToken.matches("[0-9a-f]{64}")) return Optional.empty();
        return refreshTokens.findSessionId(hash(refreshToken)).flatMap(sessions::lockById);
    }

    private Grant issueRefreshToken(AuthSession session, LocalDateTime now) {
        String value = randomValue();
        refreshTokens.saveAndFlush(new AuthRefreshToken(hash(value), session.getId(), now));
        return new Grant(session.getId(), session.getUserId(), value, session.getExpiresAt());
    }

    private String randomValue() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }

    private byte[] hash(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable");
        }
    }

    public static final class Grant {
        private final String sessionId;
        private final String userId;
        private final String refreshToken;
        private final LocalDateTime expiresAt;

        private Grant(String sessionId, String userId, String refreshToken, LocalDateTime expiresAt) {
            this.sessionId = sessionId;
            this.userId = userId;
            this.refreshToken = refreshToken;
            this.expiresAt = expiresAt;
        }

        public String getSessionId() { return sessionId; }
        public String getUserId() { return userId; }
        public String getRefreshToken() { return refreshToken; }
        public LocalDateTime getExpiresAt() { return expiresAt; }

        @Override
        public String toString() { return "SessionGrant[redacted]"; }
    }
}