package com.liushao.user.im;

import com.liushao.auth.AuthenticatedUser;
import com.liushao.auth.JwtTokenService;
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

    public JwtHandshakeInterceptor(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler webSocketHandler,
            Map<String, Object> attributes
    ) {
        String token = queryParameter(request, "token");
        if (token == null || token.isBlank()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        try {
            AuthenticatedUser user = jwtTokenService.parseToken(token);
            attributes.put("user", user.getUserId());
            return true;
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