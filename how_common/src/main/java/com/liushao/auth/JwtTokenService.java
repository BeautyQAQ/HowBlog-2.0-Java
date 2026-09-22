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

public class JwtTokenService {
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
        return issueToken(userId, mobile, null, Instant.now().plus(accessTokenTtl));
    }

    public String issueToken(String userId, String mobile, String sessionId, Instant sessionExpiresAt) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("JWT subject must not be blank");
        }
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(accessTokenTtl);
        if (sessionExpiresAt.isBefore(expiresAt)) expiresAt = sessionExpiresAt;
        if (!expiresAt.isAfter(issuedAt)) throw new IllegalArgumentException("Session expired");
        return Jwts.builder()
                .setSubject(userId)
                .claim("mobile", mobile)
                .claim("sid", sessionId)
                .setIssuedAt(Date.from(issuedAt))
                .setExpiration(Date.from(expiresAt))
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
        if (claims.getExpiration() == null) throw new IllegalArgumentException("JWT expiry missing");
        return new AuthenticatedUser(userId, claims.get("mobile", String.class),
            claims.get("sid", String.class), claims.getExpiration().toInstant());
    }

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtl.toSeconds();
    }
}