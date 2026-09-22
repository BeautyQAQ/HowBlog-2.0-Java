package com.liushao.user.service;

import com.liushao.auth.JwtTokenService;
import com.liushao.user.dao.UserDao;
import com.liushao.user.pojo.LoginRequest;
import com.liushao.user.pojo.LoginResponse;
import com.liushao.user.pojo.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@Service
public class UserService {
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final UserDao userDao;
    private final AuthSessionService sessions;

        public UserService(UserDao userDao, PasswordEncoder passwordEncoder, JwtTokenService jwtTokenService,
            AuthSessionService sessions) {
        this.userDao = userDao;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.sessions = sessions;
    }

    @Transactional(timeout = 15)
    public LoginResponse login(LoginRequest loginRequest) {
        if (loginRequest == null
                || !StringUtils.hasText(loginRequest.getMobile())
                || !StringUtils.hasText(loginRequest.getPassword())) {
            return null;
        }

        List<User> users = userDao.findAllByMobile(loginRequest.getMobile());
        if (users == null || users.size() != 1) {
            return null;
        }

        User user = users.get(0);
        if (!passwordsMatch(loginRequest.getPassword(), user.getPassword())) {
            return null;
        }

        if (!isBcryptHash(user.getPassword())) {
            user.setPassword(passwordEncoder.encode(loginRequest.getPassword()));
            userDao.save(user);
        }

        return response(user, sessions.create(user.getId()));
    }

    @Transactional(timeout = 15)
    public LoginResponse refresh(String refreshToken) {
        return sessions.rotate(refreshToken).map(grant -> {
            User user = userDao.findById(grant.getUserId()).orElse(null);
            if (user == null) {
                sessions.revoke(grant.getRefreshToken());
                return null;
            }
            return response(user, grant);
        }).orElse(null);
    }

    public boolean logout(String refreshToken) {
        return sessions.revoke(refreshToken);
    }

    private LoginResponse response(User user, AuthSessionService.Grant grant) {
        java.time.Instant expiresAt = grant.getExpiresAt().toInstant(java.time.ZoneOffset.UTC);
        String token = jwtTokenService.issueToken(user.getId(), user.getMobile(), grant.getSessionId(), expiresAt);
        long now = java.time.Instant.now().getEpochSecond();
        LoginResponse response = LoginResponse.from(user, token,
                Math.max(0, jwtTokenService.parseToken(token).getExpiresAt().getEpochSecond() - now));
        response.setRefreshToken(grant.getRefreshToken());
        response.setRefreshExpiresIn(Math.max(0, expiresAt.getEpochSecond() - now));
        return response;
    }

    private boolean passwordsMatch(String rawPassword, String storedPassword) {
        if (!StringUtils.hasText(storedPassword)) {
            return false;
        }
        if (!isBcryptHash(storedPassword)) {
            return MessageDigest.isEqual(
                    rawPassword.getBytes(StandardCharsets.UTF_8),
                    storedPassword.getBytes(StandardCharsets.UTF_8)
            );
        }
        try {
            return passwordEncoder.matches(rawPassword, storedPassword);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean isBcryptHash(String password) {
        return password.startsWith("$2a$")
                || password.startsWith("$2b$")
                || password.startsWith("$2y$");
    }
}
