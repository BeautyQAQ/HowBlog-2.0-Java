package com.liushao.user.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.liushao.user.dao.AuthRefreshTokenDao;
import com.liushao.user.dao.AuthSessionDao;
import com.liushao.user.pojo.AuthRefreshToken;
import com.liushao.user.pojo.AuthSession;

@Service
public class AuthSessionCleanupService {
    private final AuthSessionDao sessions;
    private final AuthRefreshTokenDao refreshTokens;

    public AuthSessionCleanupService(AuthSessionDao sessions, AuthRefreshTokenDao refreshTokens) {
        this.sessions = sessions;
        this.refreshTokens = refreshTokens;
    }

    @Transactional(timeout = 15)
    public int cleanSession(String sessionId, LocalDateTime cutoff, int tokenBatchSize) {
        if (cutoff == null || tokenBatchSize < 1 || tokenBatchSize > 1000) {
            throw new IllegalArgumentException("Invalid cleanup bounds");
        }
        AuthSession session = sessions.lockById(sessionId).orElse(null);
        if (session == null || session.getExpiresAt().isAfter(cutoff)) return 0;
        List<AuthRefreshToken> batch = refreshTokens.findBatchBySessionId(sessionId, PageRequest.of(0, tokenBatchSize));
        refreshTokens.deleteAllInBatch(batch);
        if (!refreshTokens.existsBySessionId(sessionId)) {
            sessions.delete(session);
            sessions.flush();
        }
        return batch.size();
    }
}