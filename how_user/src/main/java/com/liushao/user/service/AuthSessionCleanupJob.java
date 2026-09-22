package com.liushao.user.service;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.liushao.user.dao.AuthSessionDao;

@Component
@ConditionalOnProperty(name = "how.auth.cleanup.enabled", havingValue = "true")
public class AuthSessionCleanupJob {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthSessionCleanupJob.class);
    private final AuthSessionDao sessions;
    private final AuthSessionCleanupService cleanup;
    private final JdbcTemplate jdbc;
    private final int retentionHours;
    private final int sessionBatchSize;
    private final int tokenBatchSize;

    public AuthSessionCleanupJob(AuthSessionDao sessions, AuthSessionCleanupService cleanup, JdbcTemplate jdbc,
            @Value("${how.auth.cleanup.retention-hours:24}") int retentionHours,
            @Value("${how.auth.cleanup.session-batch-size:100}") int sessionBatchSize,
            @Value("${how.auth.cleanup.token-batch-size:100}") int tokenBatchSize) {
        if (retentionHours < 0 || retentionHours > 8760 || sessionBatchSize < 1 || sessionBatchSize > 1000
                || tokenBatchSize < 1 || tokenBatchSize > 1000) {
            throw new IllegalArgumentException("Invalid auth cleanup configuration");
        }
        this.sessions = sessions;
        this.cleanup = cleanup;
        this.jdbc = jdbc;
        this.retentionHours = retentionHours;
        this.sessionBatchSize = sessionBatchSize;
        this.tokenBatchSize = tokenBatchSize;
    }

    @Scheduled(initialDelayString = "${how.auth.cleanup.interval-ms:600000}",
            fixedDelayString = "${how.auth.cleanup.interval-ms:600000}")
    public void cleanExpiredSessions() {
        try {
            LocalDateTime now = jdbc.queryForObject("SELECT UTC_TIMESTAMP(6)",
                    (result, rowNumber) -> result.getObject(1, LocalDateTime.class));
            LocalDateTime cutoff = now.minusHours(retentionHours);
            int deletedTokens = 0;
            for (String sessionId : sessions.findExpiredIds(cutoff, PageRequest.of(0, sessionBatchSize))) {
                deletedTokens += cleanup.cleanSession(sessionId, cutoff, tokenBatchSize);
            }
            LOGGER.info("Auth cleanup completed; deleted refresh digests: {}", deletedTokens);
        } catch (RuntimeException exception) {
            LOGGER.warn("Auth cleanup failed; remaining records deferred to the next run");
        }
    }
}