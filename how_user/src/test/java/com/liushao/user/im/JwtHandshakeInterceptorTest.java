package com.liushao.user.im;

import com.liushao.auth.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtHandshakeInterceptorTest {
    private JwtHandshakeInterceptor interceptor;
    private JwtTokenService jwtTokenService;

    @BeforeEach
    void setUp() {
        jwtTokenService = new JwtTokenService(
                "01234567890123456789012345678901",
                Duration.ofMinutes(30)
        );
        interceptor = new JwtHandshakeInterceptor(jwtTokenService);
    }

    @Test
    void acceptsAValidTokenAndSetsTheAuthenticatedUser() {
        String token = jwtTokenService.issueToken("10001", "13800000000");
        MockHttpServletRequest request = requestForToken(token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        Map<String, Object> attributes = new HashMap<>();

        boolean accepted = interceptor.beforeHandshake(
                new ServletServerHttpRequest(request),
                new ServletServerHttpResponse(response),
                new TextWebSocketHandler(),
                attributes
        );

        assertTrue(accepted);
        assertEquals("10001", attributes.get("user"));
    }

    @Test
    void rejectsMissingOrInvalidTokens() {
        MockHttpServletRequest missingTokenRequest = new MockHttpServletRequest("GET", "/im");
        MockHttpServletResponse missingTokenResponse = new MockHttpServletResponse();

        boolean missingTokenAccepted = interceptor.beforeHandshake(
                new ServletServerHttpRequest(missingTokenRequest),
                new ServletServerHttpResponse(missingTokenResponse),
                new TextWebSocketHandler(),
                new HashMap<>()
        );

        MockHttpServletRequest invalidTokenRequest = requestForToken("not-a-token");
        MockHttpServletResponse invalidTokenResponse = new MockHttpServletResponse();
        boolean invalidTokenAccepted = interceptor.beforeHandshake(
                new ServletServerHttpRequest(invalidTokenRequest),
                new ServletServerHttpResponse(invalidTokenResponse),
                new TextWebSocketHandler(),
                new HashMap<>()
        );

        assertFalse(missingTokenAccepted);
        assertEquals(401, missingTokenResponse.getStatus());
        assertFalse(invalidTokenAccepted);
        assertEquals(401, invalidTokenResponse.getStatus());
    }

    private MockHttpServletRequest requestForToken(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/im");
        request.setQueryString("token=" + token);
        return request;
    }
}