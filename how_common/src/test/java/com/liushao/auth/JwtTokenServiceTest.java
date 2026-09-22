package com.liushao.auth;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtTokenServiceTest {
    private static final String SECRET = "01234567890123456789012345678901";

    @Test
    void issueAndParseRoundTripKeepsUserClaims() {
        JwtTokenService service = new JwtTokenService(SECRET, Duration.ofMinutes(30));

        String token = service.issueToken("10001", "13800000000");
        AuthenticatedUser user = service.parseToken(token);

        assertEquals("10001", user.getUserId());
        assertEquals("13800000000", user.getMobile());
        assertEquals(1800, service.getAccessTokenTtlSeconds());
    }

    @Test
    void rejectsTokensSignedWithAnotherSecret() {
        JwtTokenService issuer = new JwtTokenService(SECRET, Duration.ofMinutes(30));
        JwtTokenService verifier = new JwtTokenService(
                "abcdefghijklmnopqrstuvwxyz123456",
                Duration.ofMinutes(30)
        );

        String token = issuer.issueToken("10001", "13800000000");

        assertThrows(JwtException.class, () -> verifier.parseToken(token));
    }

    @Test
    void rejectsWeakSecrets() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new JwtTokenService("too-short", Duration.ofMinutes(30))
        );
    }
}