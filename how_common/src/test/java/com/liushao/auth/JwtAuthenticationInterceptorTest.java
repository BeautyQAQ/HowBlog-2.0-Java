package com.liushao.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtAuthenticationInterceptorTest {
    private static final String SECRET = "01234567890123456789012345678901";

    private JwtTokenService jwtTokenService;
    private JwtAuthenticationInterceptor interceptor;

    @BeforeEach
    void setUp() {
        jwtTokenService = new JwtTokenService(SECRET, Duration.ofMinutes(30));
        interceptor = new JwtAuthenticationInterceptor(jwtTokenService, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    @Test
    void rejectsProtectedRequestWithoutToken() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean accepted = interceptor.preHandle(
                request(null),
                response,
                handler("protectedEndpoint")
        );

        assertFalse(accepted);
        assertEquals(401, response.getStatus());
        assertEquals(20003, responseBody(response).get("code").asInt());
    }

    @Test
    void acceptsValidTokenAndClearsContextAfterRequest() throws Exception {
        String token = jwtTokenService.issueToken("10001", "13800000000");
        MockHttpServletRequest request = request("Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean accepted = interceptor.preHandle(request, response, handler("protectedEndpoint"));

        assertTrue(accepted);
        assertEquals("10001", CurrentUserContext.require().getUserId());
        interceptor.afterCompletion(request, response, handler("protectedEndpoint"), null);
        assertTrue(CurrentUserContext.get().isEmpty());
    }

    @Test
    void acceptsBearerSchemeWithoutRequiringSpecificCase() throws Exception {
        String token = jwtTokenService.issueToken("10001", "13800000000");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean accepted = interceptor.preHandle(
                request("bEaReR " + token),
                response,
                handler("protectedEndpoint")
        );

        assertTrue(accepted);
        assertEquals("10001", CurrentUserContext.require().getUserId());
    }

    @Test
    void publicRequestCanPassWithoutToken() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean accepted = interceptor.preHandle(
                request(null),
                response,
                handler("publicEndpoint")
        );

        assertTrue(accepted);
        assertTrue(CurrentUserContext.get().isEmpty());
    }

    @Test
    void rejectsMalformedBearerToken() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean accepted = interceptor.preHandle(
                request("Bearer not-a-token"),
                response,
                handler("protectedEndpoint")
        );

        assertFalse(accepted);
        assertEquals(401, response.getStatus());
    }

    private MockHttpServletRequest request(String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        return request;
    }

    private HandlerMethod handler(String methodName) throws Exception {
        Method method = TestEndpoints.class.getMethod(methodName);
        return new HandlerMethod(new TestEndpoints(), method);
    }

    private com.fasterxml.jackson.databind.JsonNode responseBody(MockHttpServletResponse response)
            throws Exception {
        return new ObjectMapper().readTree(response.getContentAsString());
    }

    public static class TestEndpoints {
        @RequireAuthentication
        public void protectedEndpoint() {
        }

        public void publicEndpoint() {
        }
    }
}