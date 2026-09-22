package com.liushao.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

public final class JwtTokenService {
    private static final int MINIMUM_SECRET_BYTES = 32;
    private final SecretKey signingKey;
    private final Duration accessTokenTtl;

    public JwtTokenService(String secret, Duration accessTokenTtl) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MINIMUM_SECRET_BYTES) {
            throw new IllegalArgumentException("JWT secret must contain at least 32 UTF-8 bytes");
        }
        if (accessTokenTtl == null || accessTokenTtl.isZero() || accessTokenTtl.isNegative()) {
            throw new IllegalArgumentException("JWT access token TTL must be positive");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtl = accessTokenTtl;
    }

    public String issueToken(String userId, String mobile) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("JWT subject must not be blank");
        }
        Instant issuedAt = Instant.now();
        return Jwts.builder()
                .setSubject(userId)
                .claim("mobile", mobile)
                .setIssuedAt(Date.from(issuedAt))
                .setExpiration(Date.from(issuedAt.plus(accessTokenTtl)))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public AuthenticatedUser parseToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("JWT token must not be blank");
        }
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
        String userId = claims.getSubject();
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("JWT subject must not be blank");
        }
        return new AuthenticatedUser(userId, claims.get("mobile", String.class));
    }

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtl.toSeconds();
    }
}