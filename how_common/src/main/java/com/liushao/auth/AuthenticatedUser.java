package com.liushao.auth;

public final class AuthenticatedUser {
    private final String userId;
    private final String mobile;

    public AuthenticatedUser(String userId, String mobile) {
        this.userId = userId;
        this.mobile = mobile;
    }

    public String getUserId() {
        return userId;
    }

    public String getMobile() {
        return mobile;
    }
}