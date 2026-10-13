package com.liushao.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class CorsOriginsTest {

    @Test
    void allowsAnyOriginWhenUnsetOrBlank() {
        assertArrayEquals(new String[] {"*"}, CorsOrigins.allowedOrigins(null));
        assertArrayEquals(new String[] {"*"}, CorsOrigins.allowedOrigins(""));
        assertArrayEquals(new String[] {"*"}, CorsOrigins.allowedOrigins(" , "));
    }

    @Test
    void trimsAndDeduplicatesConfiguredOrigins() {
        assertArrayEquals(new String[] {"https://a.test", "https://b.test"},
                CorsOrigins.allowedOrigins(" https://a.test , https://b.test ,https://a.test "));
    }

    @Test
    void keepsExplicitWildcardAndSingleOrigin() {
        assertArrayEquals(new String[] {"*"}, CorsOrigins.allowedOrigins("*"));
        assertArrayEquals(new String[] {"http://localhost:8080"},
                CorsOrigins.allowedOrigins("http://localhost:8080"));
    }
}
