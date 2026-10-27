package com.liushao.user.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;

import com.liushao.user.validation.PasswordValidator;

final class PasswordCredentials {
    private static final String SHA256_BCRYPT_PREFIX = "{bcrypt-sha256}";

    private PasswordCredentials() {
    }

    static boolean isEncoded(String stored) {
        return stored != null && (isBcrypt(stored) || stored.startsWith(SHA256_BCRYPT_PREFIX));
    }

    static String upgradeLegacy(String raw, PasswordEncoder encoder) {
        if (PasswordValidator.fitsBcrypt(raw)) {
            return encoder.encode(raw);
        }
        // 标记格式只迁移历史超长明文，保留完整密码语义，避免 BCrypt 截断。
        return SHA256_BCRYPT_PREFIX + encoder.encode(sha256(raw));
    }

    static boolean matches(String raw, String stored, PasswordEncoder encoder) {
        if (!StringUtils.hasText(raw) || !StringUtils.hasText(stored)) {
            return false;
        }
        if (stored.startsWith(SHA256_BCRYPT_PREFIX)) {
            return matchesEncoded(sha256(raw), stored.substring(SHA256_BCRYPT_PREFIX.length()), encoder);
        }
        if (isBcrypt(stored)) {
            return PasswordValidator.fitsBcrypt(raw) && matchesEncoded(raw, stored, encoder);
        }
        return MessageDigest.isEqual(raw.getBytes(StandardCharsets.UTF_8), stored.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean matchesEncoded(String raw, String stored, PasswordEncoder encoder) {
        try {
            return encoder.matches(raw, stored);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean isBcrypt(String stored) {
        return stored.startsWith("$2a$") || stored.startsWith("$2b$") || stored.startsWith("$2y$");
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
