package com.liushao.user.config;

import com.liushao.auth.JwtAuthenticationInterceptor;
import com.liushao.user.service.AuthRateLimiter;
import com.liushao.web.CorsOrigins;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AuthWebConfig implements WebMvcConfigurer {
    private final JwtAuthenticationInterceptor jwtAuthenticationInterceptor;
    private final AuthRateLimiter rateLimiter;
    private final String allowedOrigins;

    public AuthWebConfig(JwtAuthenticationInterceptor jwtAuthenticationInterceptor, AuthRateLimiter rateLimiter,
                         @Value("${how.cors.allowed-origins:*}") String allowedOrigins) {
        this.jwtAuthenticationInterceptor = jwtAuthenticationInterceptor;
        this.rateLimiter = rateLimiter;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AuthRateLimitInterceptor(rateLimiter)).order(-100);
        registry.addInterceptor(jwtAuthenticationInterceptor);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(CorsOrigins.allowedOrigins(allowedOrigins))
                .allowedMethods("*")
                .allowedHeaders("*")
                .exposedHeaders("Retry-After");
    }
}