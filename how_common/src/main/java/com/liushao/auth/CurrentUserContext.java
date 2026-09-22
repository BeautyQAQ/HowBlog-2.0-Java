package com.liushao.auth;

import java.util.Optional;

public final class CurrentUserContext {
    private static final ThreadLocal<AuthenticatedUser> CURRENT_USER = new ThreadLocal<>();

    private CurrentUserContext() {
    }

    public static void set(AuthenticatedUser user) {
        CURRENT_USER.set(user);
    }

    public static Optional<AuthenticatedUser> get() {
        return Optional.ofNullable(CURRENT_USER.get());
    }

    public static AuthenticatedUser require() {
        return get().orElseThrow(() -> new IllegalStateException("当前请求未认证"));
    }

    public static void clear() {
        CURRENT_USER.remove();
    }
}