package com.liushao.user.service;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.liushao.user.dao.AuthSessionDao;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthSessionCleanupJobTest {
    private final AuthSessionDao sessions = mock(AuthSessionDao.class);
    private final AuthSessionCleanupService cleanup = mock(AuthSessionCleanupService.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);

    @Test
    void jobIsAbsentByDefaultAndEnabledOnlyExplicitly() {
        ApplicationContextRunner context = new ApplicationContextRunner()
                .withUserConfiguration(AuthSessionCleanupJob.class)
                .withBean(AuthSessionDao.class, () -> sessions)
                .withBean(AuthSessionCleanupService.class, () -> cleanup)
                .withBean(JdbcTemplate.class, () -> jdbc);
        context.run(application -> assertTrue(application.getBeansOfType(AuthSessionCleanupJob.class).isEmpty()));
        context.withPropertyValues("how.auth.cleanup.enabled=true")
                .run(application -> assertEquals(1, application.getBeansOfType(AuthSessionCleanupJob.class).size()));
        assertTrue(mockingDetails(jdbc).getInvocations().stream()
            .noneMatch(invocation -> invocation.getMethod().getName().startsWith("query")));
        verifyNoInteractions(sessions, cleanup);
    }

    @Test
    @SuppressWarnings({"unchecked", "null"})
    void usesDatabaseUtcRetentionAndBothBatchLimits() {
        LocalDateTime databaseNow = LocalDateTime.of(2000, 1, 2, 0, 0);
        LocalDateTime cutoff = databaseNow.minusHours(24);
        when(jdbc.queryForObject(eq("SELECT UTC_TIMESTAMP(6)"), any(RowMapper.class))).thenReturn(databaseNow);
        when(sessions.findExpiredIds(cutoff, PageRequest.of(0, 2))).thenReturn(List.of("first", "second"));
        new AuthSessionCleanupJob(sessions, cleanup, jdbc, 24, 2, 3).cleanExpiredSessions();
        verify(cleanup).cleanSession("first", cutoff, 3);
        verify(cleanup).cleanSession("second", cutoff, 3);
        verifyNoMoreInteractions(cleanup);
    }

    @Test
    @SuppressWarnings({"unchecked", "null"})
    void failureStopsRunAndNextRunCanRetry() {
        LocalDateTime cutoff = LocalDateTime.of(2000, 1, 1, 0, 0);
        when(jdbc.queryForObject(eq("SELECT UTC_TIMESTAMP(6)"), any(RowMapper.class))).thenReturn(cutoff);
        when(sessions.findExpiredIds(cutoff, PageRequest.of(0, 2))).thenReturn(List.of("first", "second"));
        when(cleanup.cleanSession("first", cutoff, 3)).thenThrow(new IllegalStateException("internal"))
                .thenReturn(1);
        AuthSessionCleanupJob job = new AuthSessionCleanupJob(sessions, cleanup, jdbc, 0, 2, 3);
        assertDoesNotThrow(job::cleanExpiredSessions);
        verify(cleanup, never()).cleanSession("second", cutoff, 3);
        job.cleanExpiredSessions();
        verify(cleanup).cleanSession("second", cutoff, 3);
    }

    @Test
    void invalidBoundsFailAtStartup() {
        assertThrows(IllegalArgumentException.class, () -> new AuthSessionCleanupJob(sessions, cleanup, jdbc, -1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new AuthSessionCleanupJob(sessions, cleanup, jdbc, 24, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new AuthSessionCleanupJob(sessions, cleanup, jdbc, 24, 1, 1001));
    }
}