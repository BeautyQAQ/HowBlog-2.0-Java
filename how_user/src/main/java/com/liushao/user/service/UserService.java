package com.liushao.user.service;

import com.liushao.auth.JwtTokenService;
import com.liushao.user.dao.UserDao;
import com.liushao.user.pojo.LoginRequest;
import com.liushao.user.pojo.LoginResponse;
import com.liushao.user.pojo.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@Service
public class UserService {
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final UserDao userDao;

    public UserService(UserDao userDao, PasswordEncoder passwordEncoder, JwtTokenService jwtTokenService) {
        this.userDao = userDao;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

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

        String token = jwtTokenService.issueToken(user.getId(), user.getMobile());
        return LoginResponse.from(user, token, jwtTokenService.getAccessTokenTtlSeconds());
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
