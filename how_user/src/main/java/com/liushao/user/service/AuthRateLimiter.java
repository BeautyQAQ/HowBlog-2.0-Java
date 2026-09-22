package com.liushao.user.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.text.Normalizer;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class AuthRateLimiter {
    private static final String PREFIX = "how.auth.rate-limit.";
    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>();
    static {
        SCRIPT.setLocation(new ClassPathResource("auth-rate-limit.lua"));
        SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate redis;
    private final Map<Scope, Integer> limits = new EnumMap<>(Scope.class);
    private final boolean enabled;
    private final int windowSeconds;
    private final String keyPrefix;
    private final SecretKeySpec hashKey;

    public AuthRateLimiter(StringRedisTemplate redis, Environment environment) {
        this.redis = redis;
        this.enabled = environment.getProperty(PREFIX + "enabled", Boolean.class, true);
        this.windowSeconds = positive(environment, "window-seconds", 60, 3600);
        this.keyPrefix = environment.getProperty(PREFIX + "key-prefix", "howblog:auth:rate:v1:");
        if (!keyPrefix.matches("[a-zA-Z0-9:_-]{1,100}")) {
            throw new IllegalArgumentException("Invalid auth rate-limit key prefix");
        }
        this.hashKey = new SecretKeySpec(environment.getRequiredProperty("how.auth.jwt.secret")
                .getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        for (Scope scope : Scope.values()) {
            limits.put(scope, positive(environment, scope.property + "-limit", scope.defaultLimit, 100000));
        }
    }

    public void check(Scope scope, String identity) {
        if (!enabled) return;
        String value = identity == null ? "" : identity;
        if (scope == Scope.LOGIN_ACCOUNT) {
            value = Normalizer.normalize(value, Normalizer.Form.NFKC).strip().toLowerCase(Locale.ROOT);
        }
        String key = keyPrefix + scope.property + ":" + digest(value);
        Long retryMillis;
        try {
            retryMillis = redis.execute(SCRIPT, List.of(key), String.valueOf(limits.get(scope)),
                    String.valueOf(windowSeconds * 1000L));
        } catch (RuntimeException exception) {
            throw new Rejected(0);
        }
        if (retryMillis == null || retryMillis < 0) throw new Rejected(0);
        if (retryMillis > 0) throw new Rejected((retryMillis - 1) / 1000 + 1);
    }

    private String digest(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(hashKey);
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Auth rate-limit hashing unavailable");
        }
    }

    private int positive(Environment environment, String property, int fallback, int maximum) {
        int value = environment.getProperty(PREFIX + property, Integer.class, fallback);
        if (value < 1 || value > maximum) throw new IllegalArgumentException("Invalid auth rate-limit bounds");
        return value;
    }

    public enum Scope {
        LOGIN_IP("login-ip", 20), LOGIN_ACCOUNT("login-account", 10),
        REFRESH_IP("refresh-ip", 60), LOGOUT_IP("logout-ip", 30);

        private final String property;
        private final int defaultLimit;

        Scope(String property, int defaultLimit) {
            this.property = property;
            this.defaultLimit = defaultLimit;
        }
    }

    public static class Rejected extends RuntimeException {
        private final long retryAfterSeconds;

        public Rejected(long retryAfterSeconds) {
            super("Authentication request rejected");
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public long getRetryAfterSeconds() { return retryAfterSeconds; }
    }
}