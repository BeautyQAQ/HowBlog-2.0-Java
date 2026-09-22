package com.liushao.user.config;

import com.liushao.auth.JwtAuthenticationInterceptor;
import com.liushao.user.service.AuthRateLimiter;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AuthWebConfig implements WebMvcConfigurer {
    private final JwtAuthenticationInterceptor jwtAuthenticationInterceptor;
    private final AuthRateLimiter rateLimiter;

    public AuthWebConfig(JwtAuthenticationInterceptor jwtAuthenticationInterceptor, AuthRateLimiter rateLimiter) {
        this.jwtAuthenticationInterceptor = jwtAuthenticationInterceptor;
        this.rateLimiter = rateLimiter;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AuthRateLimitInterceptor(rateLimiter)).order(-100);
        registry.addInterceptor(jwtAuthenticationInterceptor);
    }
}