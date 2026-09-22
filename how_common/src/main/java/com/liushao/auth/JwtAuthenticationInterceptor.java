package com.liushao.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liushao.entity.Result;
import com.liushao.entity.StatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class JwtAuthenticationInterceptor implements HandlerInterceptor {
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenService jwtTokenService;
    private final ObjectMapper objectMapper;
    private final SessionVerifier sessions;

    public JwtAuthenticationInterceptor(JwtTokenService jwtTokenService, ObjectMapper objectMapper, SessionVerifier sessions) {
        this.jwtTokenService = jwtTokenService;
        this.objectMapper = objectMapper;
        this.sessions = sessions;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) throws IOException {
        CurrentUserContext.clear();
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (handler instanceof HandlerMethod method
                && method.hasMethodAnnotation(RefreshCredentialEndpoint.class)) {
            return true;
        }

        String authorization = request.getHeader("Authorization");
        AuthenticatedUser user = null;
        if (authorization != null && !authorization.isBlank()) {
            if (!authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
                return reject(response);
            }
            try {
                user = jwtTokenService.parseToken(authorization.substring(BEARER_PREFIX.length()).trim());
            } catch (RuntimeException exception) {
                return reject(response);
            }
            if (user.getSessionId() == null || !user.getSessionId().matches("[0-9a-f]{64}")) {
                return reject(response);
            }
            if (!sessions.isActive(user.getSessionId(), user.getUserId())) return reject(response);
        }

        if (requiresAuthentication(handler) && user == null) {
            return reject(response);
        }
        if (user != null) {
            CurrentUserContext.set(user);
        }
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception exception
    ) {
        CurrentUserContext.clear();
    }

    private boolean requiresAuthentication(Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return false;
        }
        return handlerMethod.hasMethodAnnotation(RequireAuthentication.class)
                || handlerMethod.getBeanType().isAnnotationPresent(RequireAuthentication.class);
    }

    private boolean reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader("WWW-Authenticate", "Bearer");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getWriter(),
                new Result(false, StatusCode.ACCESSERROR, "请先登录")
        );
        return false;
    }
}