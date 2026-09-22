package com.liushao.user.config;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.liushao.user.controller.UserController;
import com.liushao.user.service.AuthRateLimiter;
import com.liushao.user.service.AuthRateLimiter.Scope;

public class AuthRateLimitInterceptor implements HandlerInterceptor {
    private final AuthRateLimiter limiter;

    public AuthRateLimitInterceptor(AuthRateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!"POST".equals(request.getMethod()) || !(handler instanceof HandlerMethod method)
                || !UserController.class.isAssignableFrom(method.getBeanType())) return true;
        Scope scope = switch (method.getMethod().getName()) {
            case "login" -> Scope.LOGIN_IP;
            case "refresh" -> Scope.REFRESH_IP;
            case "logout" -> Scope.LOGOUT_IP;
            default -> null;
        };
        if (scope != null) {
            response.setHeader("Cache-Control", "no-store");
            limiter.check(scope, request.getRemoteAddr());
        }
        return true;
    }
}