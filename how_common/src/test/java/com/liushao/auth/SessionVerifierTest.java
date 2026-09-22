package com.liushao.auth;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SessionVerifierTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final SessionVerifier verifier = new SessionVerifier(jdbc);

    @Test
    void rejectsLegacyAndMalformedSessionWithoutQuery() {
        assertFalse(verifier.isActive(null, "user"));
        assertFalse(verifier.isActive("bad", "user"));
        assertFalse(verifier.isActive("a".repeat(64), " "));
        verifyNoInteractions(jdbc);
    }

    @Test
    void queriesEveryTimeAndBindsBothIdentities() {
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq("a".repeat(64)), eq("user")))
                .thenReturn(1L, 0L);
        assertTrue(verifier.isActive("a".repeat(64), "user"));
        assertFalse(verifier.isActive("a".repeat(64), "user"));
        verify(jdbc, times(2)).queryForObject(contains("UTC_TIMESTAMP(6)"), eq(Long.class),
                eq("a".repeat(64)), eq("user"));
    }

    @Test
    void doesNotTurnStorageFailureIntoAuthorization() {
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq("a".repeat(64)), eq("user")))
                .thenThrow(new DataAccessResourceFailureException("test failure"));
        assertThrows(DataAccessResourceFailureException.class,
                () -> verifier.isActive("a".repeat(64), "user"));
    }
}