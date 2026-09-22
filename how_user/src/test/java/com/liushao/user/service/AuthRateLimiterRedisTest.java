package com.liushao.user.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.env.MockEnvironment;

import com.liushao.user.service.AuthRateLimiter.Scope;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "HOW_AUTH_REDIS_TEST_PORT", matches = "[0-9]+")
class AuthRateLimiterRedisTest {
    private LettuceConnectionFactory connection;
    private StringRedisTemplate redis;
    private MockEnvironment environment;
    private String prefix;

    @BeforeEach
    void connectToExplicitLocalTestServer() {
        int port = Integer.parseInt(System.getenv("HOW_AUTH_REDIS_TEST_PORT"));
        assertTrue(port > 0 && port <= 65535);
        connection = new LettuceConnectionFactory(new RedisStandaloneConfiguration("127.0.0.1", port),
                LettuceClientConfiguration.builder().commandTimeout(Duration.ofSeconds(2)).build());
        connection.afterPropertiesSet();
        redis = new StringRedisTemplate(connection);
        prefix = "howblog:auth:test:" + UUID.randomUUID() + ":";
        environment = new MockEnvironment().withProperty("how.auth.jwt.secret", "test-only-key")
                .withProperty("how.auth.rate-limit.key-prefix", prefix)
                .withProperty("how.auth.rate-limit.login-ip-limit", "5")
                .withProperty("how.auth.rate-limit.window-seconds", "30");
    }

    @AfterEach
    void closeConnection() {
        if (connection != null) connection.destroy();
    }

    @Test
    void twoInstancesShareExactlyOneAtomicQuota() throws Exception {
        AuthRateLimiter first = new AuthRateLimiter(redis, environment);
        AuthRateLimiter second = new AuthRateLimiter(redis, environment);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(12);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int attempt = 0; attempt < 24; attempt++) {
                AuthRateLimiter limiter = attempt % 2 == 0 ? first : second;
                results.add(executor.submit(() -> {
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    try {
                        limiter.check(Scope.LOGIN_IP, "test-client");
                        return true;
                    } catch (AuthRateLimiter.Rejected rejected) {
                        assertTrue(rejected.getRetryAfterSeconds() > 0);
                        return false;
                    }
                }));
            }
            start.countDown();
            int accepted = 0;
            for (Future<Boolean> result : results) if (result.get(10, TimeUnit.SECONDS)) accepted++;
            assertEquals(5, accepted);
            first.check(Scope.LOGOUT_IP, "test-client");
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void rejectedRequestsDoNotExtendWindowAndExpiryRestoresQuota() {
        environment.withProperty("how.auth.rate-limit.window-seconds", "1")
                .withProperty("how.auth.rate-limit.login-ip-limit", "1");
        AuthRateLimiter limiter = new AuthRateLimiter(redis, environment);
        limiter.check(Scope.LOGIN_IP, "test-client");
        String key = redis.keys(prefix + "*").iterator().next();
        Long before = redis.getExpire(key, TimeUnit.MILLISECONDS);
        assertEquals(1, assertThrows(AuthRateLimiter.Rejected.class,
                () -> limiter.check(Scope.LOGIN_IP, "test-client")).getRetryAfterSeconds());
        assertTrue(redis.getExpire(key, TimeUnit.MILLISECONDS) <= before);
        await().atMost(Duration.ofSeconds(5)).until(() -> !Boolean.TRUE.equals(redis.hasKey(key)));
        assertDoesNotThrow(() -> limiter.check(Scope.LOGIN_IP, "test-client"));
    }
}