package com.liushao.user.im;

import com.liushao.auth.AuthenticatedUser;
import com.liushao.auth.JwtTokenService;
import com.liushao.auth.SessionVerifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {
    private final JwtTokenService jwtTokenService;
    private final SessionVerifier sessions;

    public JwtHandshakeInterceptor(JwtTokenService jwtTokenService, SessionVerifier sessions) {
        this.jwtTokenService = jwtTokenService;
        this.sessions = sessions;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler webSocketHandler,
            Map<String, Object> attributes
    ) {
        try {
            String token = queryParameter(request, "token");
            AuthenticatedUser user = jwtTokenService.parseToken(token);
            if (!sessions.isActive(user.getSessionId(), user.getUserId())) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
            attributes.put("user", user.getUserId());
            attributes.put("identity", user);
            return true;
        } catch (org.springframework.dao.DataAccessException exception) {
            response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
            return false;
        } catch (RuntimeException exception) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler webSocketHandler,
            Exception exception
    ) {
    }

    private String queryParameter(ServerHttpRequest request, String name) {
        String query = request.getURI().getRawQuery();
        if (query == null || query.isBlank()) {
            return null;
        }
        for (String parameter : query.split("&")) {
            String[] pair = parameter.split("=", 2);
            if (pair.length == 2 && name.equals(pair[0])) {
                return URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}