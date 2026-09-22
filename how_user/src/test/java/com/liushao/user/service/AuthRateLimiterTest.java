package com.liushao.user.service;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mock.env.MockEnvironment;

import com.liushao.user.service.AuthRateLimiter.Scope;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthRateLimiterTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final MockEnvironment environment = new MockEnvironment()
            .withProperty("how.auth.jwt.secret", "test-only-signing-key-not-for-production");

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void sharesNormalizedHashedKeysAcrossInstancesAndSeparatesScopes() {
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString())).thenReturn(0L);
        AuthRateLimiter first = new AuthRateLimiter(redis, environment);
        AuthRateLimiter second = new AuthRateLimiter(redis, environment);
        first.check(Scope.LOGIN_ACCOUNT, " Test-Mobile ");
        second.check(Scope.LOGIN_ACCOUNT, "test-mobile");
        first.check(Scope.LOGIN_IP, "test-mobile");
        first.check(Scope.REFRESH_IP, "test-mobile");
        first.check(Scope.LOGOUT_IP, "test-mobile");
        ArgumentCaptor<List> keys = ArgumentCaptor.forClass(List.class);
        verify(redis, times(5)).execute(any(RedisScript.class), keys.capture(), anyString(), eq("60000"));
        assertEquals(keys.getAllValues().get(0), keys.getAllValues().get(1));
        assertEquals(4, keys.getAllValues().stream().distinct().count());
        for (List keyList : keys.getAllValues()) {
            String key = keyList.get(0).toString();
            assertFalse(key.contains("test-mobile"));
            assertTrue(key.matches("howblog:auth:rate:v1:[a-z-]+:[0-9a-f]{64}"));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void roundsRetryAfterUpAndPassesConfiguredLimitAndWindow() {
        environment.withProperty("how.auth.rate-limit.login-ip-limit", "2")
                .withProperty("how.auth.rate-limit.window-seconds", "3");
        when(redis.execute(any(RedisScript.class), anyList(), eq("2"), eq("3000"))).thenReturn(1001L);
        AuthRateLimiter limiter = new AuthRateLimiter(redis, environment);
        assertEquals(2, assertThrows(AuthRateLimiter.Rejected.class,
                () -> limiter.check(Scope.LOGIN_IP, "127.0.0.1")).getRetryAfterSeconds());
    }

    @Test
    @SuppressWarnings("unchecked")
    void redisFailureAndMissingOrInvalidResultsFailClosedWithoutLeakingDetails() {
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString()))
                .thenReturn(null).thenReturn(-1L).thenThrow(new IllegalStateException("sensitive detail"));
        AuthRateLimiter limiter = new AuthRateLimiter(redis, environment);
        for (int attempt = 0; attempt < 3; attempt++) {
            AuthRateLimiter.Rejected rejected = assertThrows(AuthRateLimiter.Rejected.class,
                    () -> limiter.check(Scope.LOGIN_IP, "127.0.0.1"));
            assertEquals(0, rejected.getRetryAfterSeconds());
            assertFalse(rejected.getMessage().contains("sensitive"));
            assertNull(rejected.getCause());
        }
    }

    @Test
    void explicitDisableDoesNotAccessRedis() {
        environment.withProperty("how.auth.rate-limit.enabled", "false");
        new AuthRateLimiter(redis, environment).check(Scope.LOGIN_IP, "127.0.0.1");
        verifyNoInteractions(redis);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "3601"})
    void rejectsInvalidWindow(String value) {
        environment.withProperty("how.auth.rate-limit.window-seconds", value);
        assertThrows(IllegalArgumentException.class, () -> new AuthRateLimiter(redis, environment));
    }

    @Test
    void rejectsUnboundedQuotaAndInvalidNamespace() {
        environment.withProperty("how.auth.rate-limit.login-ip-limit", "100001");
        assertThrows(IllegalArgumentException.class, () -> new AuthRateLimiter(redis, environment));
        environment.withProperty("how.auth.rate-limit.login-ip-limit", "20")
                .withProperty("how.auth.rate-limit.key-prefix", "bad namespace");
        assertThrows(IllegalArgumentException.class, () -> new AuthRateLimiter(redis, environment));
    }
}