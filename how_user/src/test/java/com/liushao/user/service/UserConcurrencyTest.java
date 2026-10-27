package com.liushao.user.service;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liushao.user.dao.AuthSessionDao;
import com.liushao.user.dao.UserDao;
import com.liushao.user.pojo.ChangePasswordRequest;
import com.liushao.user.pojo.LoginResponse;
import com.liushao.user.pojo.ProfileUpdateRequest;
import com.liushao.user.pojo.User;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DataJpaTest(showSql = false, properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "how.auth.jwt.secret=test-only-signing-key-not-for-production",
        "logging.level.root=WARN"
})
@Import({UserService.class, AuthSessionService.class, UserConcurrencyTest.Configuration.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class UserConcurrencyTest {
    private static final String OLD_PASSWORD = "original-password";
    private static final String FIRST_PASSWORD = "first-new-password";
    private static final String SECOND_PASSWORD = "second-new-password";
    private static final String SESSION_ID = "a".repeat(64);

    @Autowired private UserService service;
    @Autowired private PlatformTransactionManager transactions;
    @SpyBean private UserDao users;
    @SpyBean private PasswordEncoder passwords;
    @SpyBean private AuthSessionService sessions;
    @Autowired private AuthSessionDao sessionRows;
    @MockBean private ObjectMapper objectMapper;
    @PersistenceContext private EntityManager entityManager;
    private String userId;

    @BeforeEach
    void createUser() {
        userId = "concurrency-" + UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setPassword(passwords.encode(OLD_PASSWORD));
        user.setNickname("original");
        user.setFanscount(0);
        user.setFollowcount(0);
        users.saveAndFlush(user);
    }

    @Test
    void profileSaveCannotRestorePasswordChangedByAnotherTransaction() throws Exception {
        CountDownLatch profileAtSave = new CountDownLatch(1);
        CountDownLatch releaseProfile = new CountDownLatch(1);
        pauseProfileSave(profileAtSave, releaseProfile);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> profile = executor.submit(this::updateNickname);
            assertTrue(profileAtSave.await(5, TimeUnit.SECONDS));
            Future<UserService.PasswordChangeResult> changed = executor.submit(
                    () -> service.changePassword(userId, SESSION_ID, change(FIRST_PASSWORD)));
            allowCompetingTransactionToReachWrite(changed);
            releaseProfile.countDown();
            profile.get(15, TimeUnit.SECONDS);
            assertEquals(UserService.PasswordChangeResult.SUCCESS, changed.get(15, TimeUnit.SECONDS));
            User persisted = users.findById(userId).orElseThrow();
            assertEquals("updated", persisted.getNickname());
            assertTrue(passwords.matches(FIRST_PASSWORD, persisted.getPassword()),
                    "saving profile must not restore the previously loaded password");
        } finally {
            releaseProfile.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));
        }
    }

    @Test
    void profileSaveCannotLoseAtomicFollowCounterUpdates() throws Exception {
        CountDownLatch profileAtSave = new CountDownLatch(1);
        CountDownLatch releaseProfile = new CountDownLatch(1);
        pauseProfileSave(profileAtSave, releaseProfile);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> profile = executor.submit(this::updateNickname);
            assertTrue(profileAtSave.await(5, TimeUnit.SECONDS));
            Future<?> counters = executor.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                users.incrementFans(userId);
                users.incrementFollow(userId);
                return null;
            }));
            allowCompetingTransactionToReachWrite(counters);
            releaseProfile.countDown();
            profile.get(15, TimeUnit.SECONDS);
            counters.get(15, TimeUnit.SECONDS);
            User persisted = users.findById(userId).orElseThrow();
            assertEquals("updated", persisted.getNickname());
            assertEquals(1, persisted.getFanscount());
            assertEquals(1, persisted.getFollowcount());
        } finally {
            releaseProfile.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));
        }
    }

    @Test
    void concurrentPasswordChangeMustRecheckCurrentPasswordAfterTheFirstCommit() throws Exception {
        CountDownLatch firstAtEncode = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicBoolean firstEncode = new AtomicBoolean(true);
        doAnswer(invocation -> {
            if (firstEncode.compareAndSet(true, false)) {
                firstAtEncode.countDown();
                assertTrue(releaseFirst.await(10, TimeUnit.SECONDS));
            }
            return invocation.callRealMethod();
        }).when(passwords).encode(any());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<UserService.PasswordChangeResult> first = executor.submit(
                    () -> service.changePassword(userId, SESSION_ID, change(FIRST_PASSWORD)));
            assertTrue(firstAtEncode.await(5, TimeUnit.SECONDS));
            Future<UserService.PasswordChangeResult> second = executor.submit(
                    () -> service.changePassword(userId, SESSION_ID, change(SECOND_PASSWORD)));
            allowCompetingTransactionToReachWrite(second);
            releaseFirst.countDown();
            assertEquals(UserService.PasswordChangeResult.SUCCESS, first.get(15, TimeUnit.SECONDS));
            assertEquals(UserService.PasswordChangeResult.WRONG_PASSWORD, second.get(15, TimeUnit.SECONDS),
                    "the old password must not authorize both concurrent changes");
            assertTrue(passwords.matches(FIRST_PASSWORD, users.findById(userId).orElseThrow().getPassword()));
            verify(sessions, times(1)).revokeOthers(userId, SESSION_ID);
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));
        }
    }

    @Test
    void refreshHoldingSessionLockDoesNotDeadlockPasswordChangeHoldingUserLock() throws Exception {
        AuthSessionService.Grant current = sessions.create(userId);
        AuthSessionService.Grant other = sessions.create(userId);
        CountDownLatch sessionLocked = new CountDownLatch(1);
        CountDownLatch passwordAtEncode = new CountDownLatch(1);
        CountDownLatch refreshMayContinue = new CountDownLatch(1);
        doAnswer(invocation -> {
            passwordAtEncode.countDown();
            return invocation.callRealMethod();
        }).when(passwords).encode(any());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<LoginResponse> refresh = executor.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                sessionRows.lockById(other.getSessionId()).orElseThrow();
                sessionLocked.countDown();
                try {
                    assertTrue(refreshMayContinue.await(10, TimeUnit.SECONDS));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupted);
                }
                return service.refresh(other.getRefreshToken());
            }));
            assertTrue(sessionLocked.await(5, TimeUnit.SECONDS));
            Future<UserService.PasswordChangeResult> changed = executor.submit(
                    () -> service.changePassword(userId, current.getSessionId(), change(FIRST_PASSWORD)));
            assertTrue(passwordAtEncode.await(5, TimeUnit.SECONDS));
            refreshMayContinue.countDown();
            assertNotNull(refresh.get(15, TimeUnit.SECONDS));
            assertEquals(UserService.PasswordChangeResult.SUCCESS, changed.get(15, TimeUnit.SECONDS));
            assertNotNull(sessionRows.findById(other.getSessionId()).orElseThrow().getRevokedAt());
            assertNull(sessionRows.findById(current.getSessionId()).orElseThrow().getRevokedAt());
            assertTrue(passwords.matches(FIRST_PASSWORD, users.findById(userId).orElseThrow().getPassword()));
        } finally {
            refreshMayContinue.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));
        }
    }

    private void pauseProfileSave(CountDownLatch reached, CountDownLatch release) {
        doAnswer(invocation -> {
            User user = invocation.getArgument(0);
            if (userId.equals(user.getId()) && "updated".equals(user.getNickname())) {
                reached.countDown();
                assertTrue(release.await(10, TimeUnit.SECONDS));
            }
            // JpaRepository.save merges an existing entity inside this same service transaction.
            return entityManager.merge(user);
        }).when(users).save(any(User.class));
    }

    private void updateNickname() {
        ProfileUpdateRequest request = new ProfileUpdateRequest();
        request.setNickname("updated");
        service.updateProfile(userId, request);
    }

    private ChangePasswordRequest change(String newPassword) {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setOldPassword(OLD_PASSWORD);
        request.setNewPassword(newPassword);
        return request;
    }

    private void allowCompetingTransactionToReachWrite(Future<?> competitor) throws Exception {
        try {
            competitor.get(1, TimeUnit.SECONDS);
        } catch (TimeoutException expectedWhenRowIsLocked) {
            // A locked writer completes after the transaction holding the row is released.
        }
    }

    @TestConfiguration
    static class Configuration {
        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder(4);
        }

    }
}
