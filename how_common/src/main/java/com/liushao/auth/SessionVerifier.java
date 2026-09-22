package com.liushao.auth;

import org.springframework.jdbc.core.JdbcTemplate;

public class SessionVerifier {
    private final JdbcTemplate jdbc;

    public SessionVerifier(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean isActive(String sessionId, String userId) {
        if (sessionId == null || !sessionId.matches("[0-9a-f]{64}") || userId == null || userId.isBlank()) {
            return false;
        }
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM tb_auth_session auth_session "
                + "JOIN tb_user account ON account.id = auth_session.user_id "
                + "WHERE auth_session.id = ? AND auth_session.user_id = ? "
                + "AND auth_session.revoked_at IS NULL AND auth_session.expires_at > UTC_TIMESTAMP(6)",
                Long.class, sessionId, userId);
        return count != null && count == 1;
    }
}