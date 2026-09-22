package com.liushao.auth;

public final class AuthenticatedUser {
    private final String userId;
    private final String mobile;
    private final String sessionId;
    private final java.time.Instant expiresAt;

    public AuthenticatedUser(String userId, String mobile) {
        this(userId, mobile, null, java.time.Instant.EPOCH);
    }

    public AuthenticatedUser(String userId, String mobile, String sessionId, java.time.Instant expiresAt) {
        this.userId = userId;
        this.mobile = mobile;
        this.sessionId = sessionId;
        this.expiresAt = expiresAt;
    }

    public String getSessionId() { return sessionId; }
    public java.time.Instant getExpiresAt() { return expiresAt; }

    public String getUserId() {
        return userId;
    }

    public String getMobile() {
        return mobile;
    }
}