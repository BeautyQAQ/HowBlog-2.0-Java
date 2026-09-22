package com.liushao.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class JwtConfiguration {

    @Bean
    public JwtTokenService jwtTokenService(
            @Value("${how.auth.jwt.secret}") String secret,
            @Value("${how.auth.jwt.access-token-ttl-seconds:1800}") long ttlSeconds
    ) {
        return new JwtTokenService(secret, Duration.ofSeconds(ttlSeconds));
    }

    @Bean
    public JwtAuthenticationInterceptor jwtAuthenticationInterceptor(
            JwtTokenService jwtTokenService,
            ObjectMapper objectMapper
    ) {
        return new JwtAuthenticationInterceptor(jwtTokenService, objectMapper);
    }
}