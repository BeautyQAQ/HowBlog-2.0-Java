package com.liushao.user.service;

import com.liushao.auth.JwtTokenService;
import com.liushao.user.dao.UserDao;
import com.liushao.user.pojo.LoginRequest;
import com.liushao.user.pojo.LoginResponse;
import com.liushao.user.pojo.ProfileResponse;
import com.liushao.user.pojo.ProfileUpdateRequest;
import com.liushao.user.pojo.RegisterRequest;
import com.liushao.user.pojo.User;
import com.liushao.util.IdWorker;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;
import java.util.List;

@Service
public class UserService {
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final UserDao userDao;
    private final AuthSessionService sessions;
    private final IdWorker idWorker;

    public UserService(UserDao userDao, PasswordEncoder passwordEncoder, JwtTokenService jwtTokenService,
            AuthSessionService sessions, IdWorker idWorker) {
        this.userDao = userDao;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.sessions = sessions;
        this.idWorker = idWorker;
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
    public RegisterResult register(RegisterRequest request) {
        // 服务层校验与 RegisterRequest 注解保持同一规则，防止绕过控制器直接调用时写入非法数据
        if (request == null
                || !StringUtils.hasText(request.getMobile())
                || !request.getMobile().matches("1\\d{10}")
                || !StringUtils.hasText(request.getPassword())
                || request.getPassword().length() < 8
                || request.getPassword().length() > 64) {
            return RegisterResult.INVALID;
        }

        List<User> existing = userDao.findAllByMobile(request.getMobile());
        if (existing != null && !existing.isEmpty()) {
            return RegisterResult.DUPLICATE;
        }

        User user = new User();
        user.setId(String.valueOf(idWorker.nextId()));
        user.setMobile(request.getMobile());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        String nickname = request.getNickname() == null ? null : request.getNickname().strip();
        user.setNickname(nickname == null || nickname.isEmpty() ? null : nickname);
        user.setRegdate(new Date());
        user.setOnline(0L);
        user.setFanscount(0);
        user.setFollowcount(0);
        userDao.save(user);
        return RegisterResult.SUCCESS;
    }

    public enum RegisterResult {
        SUCCESS, INVALID, DUPLICATE
    }

    @Transactional(timeout = 15)
    public ProfileResponse profile(String userId) {
        return userDao.findById(userId).map(ProfileResponse::from).orElse(null);
    }

    @Transactional(timeout = 15)
    public ProfileResponse updateProfile(String userId, ProfileUpdateRequest request) {
        return userDao.findById(userId).map(existing -> {
            if (request.getNickname() != null) existing.setNickname(request.getNickname().strip());
            if (request.getAvatar() != null) existing.setAvatar(request.getAvatar().strip());
            if (request.getSex() != null) existing.setSex(request.getSex().strip());
            if (request.getBirthday() != null) existing.setBirthday(request.getBirthday());
            if (request.getEmail() != null) existing.setEmail(request.getEmail().strip());
            if (request.getInterest() != null) existing.setInterest(request.getInterest().strip());
            if (request.getPersonality() != null) existing.setPersonality(request.getPersonality().strip());
            existing.setUpdatedate(new Date());
            userDao.save(existing);
            return ProfileResponse.from(existing);
        }).orElse(null);
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
