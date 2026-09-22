package com.liushao.user.auth;

import java.nio.file.Path;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import com.liushao.user.dao.AuthSessionDao;
import com.liushao.user.dao.UserDao;
import com.liushao.user.service.AuthSessionService;
import com.liushao.user.service.AuthSessionService.Grant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@EnabledIfEnvironmentVariable(named = "HOW_AUTH_SERVICE_TEST_CONFIG", matches = ".+")
class AuthSessionMySqlTest {
    @Test
    void verifiesJpaSessionsWithRealMySqlTransactions() {
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger)
                org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ch.qos.logback.classic.Level previousLevel = logger.getLevel();
        logger.setLevel(ch.qos.logback.classic.Level.OFF);
        String stage = "configuration";
        try {
            DataSource source = testDataSource();
            stage = "database preconditions";
            try (Connection connection = source.getConnection()) {
                assertEquals("MySQL", connection.getMetaData().getDatabaseProductName());
                assertTrue(connection.getMetaData().getDatabaseMajorVersion() >= 8);
                assertEquals(Connection.TRANSACTION_REPEATABLE_READ, connection.getTransactionIsolation());
            }
            String userId = "auth-test-" + UUID.randomUUID();
            JdbcTemplate jdbc = new JdbcTemplate(source);
            stage = "JPA service transactions";
            try (AnnotationConfigApplicationContext context = context(source, userId)) {
                try {
                    verifyTransactions(context, source, userId, jdbc);
                } finally {
                    jdbc.update("UPDATE tb_auth_session SET revoked_at = UTC_TIMESTAMP(6), "
                            + "expires_at = UTC_TIMESTAMP(6) WHERE user_id = ?", userId);
                }
            }
        } catch (Throwable failure) {
            int line = java.util.Arrays.stream(failure.getStackTrace())
                .filter(frame -> frame.getClassName().equals(getClass().getName()))
                .mapToInt(StackTraceElement::getLineNumber).findFirst().orElse(-1);
            fail("MySQL session verification failed at " + stage + " (details suppressed): "
                + failure.getClass().getSimpleName() + "; test line=" + line);
        } finally {
            logger.setLevel(previousLevel);
        }
    }

    private void verifyTransactions(AnnotationConfigApplicationContext context, DataSource source,
            String userId, JdbcTemplate jdbc) throws Exception {
        AuthSessionService service = context.getBean(AuthSessionService.class);
        AuthSessionDao sessions = context.getBean(AuthSessionDao.class);
        TransactionTemplate transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        Grant original = service.create(userId);
        Grant independent = service.create(userId);
        byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(original.getRefreshToken()
                .getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        assertArrayEquals(digest, jdbc.queryForObject(
                "SELECT token_hash FROM tb_auth_refresh_token WHERE session_id = ?", byte[].class, original.getSessionId()));
        assertEquals(original.getExpiresAt(), jdbc.queryForObject(
                "SELECT expires_at FROM tb_auth_session WHERE id = ?", LocalDateTime.class, original.getSessionId()));
        Grant rotated = service.rotate(original.getRefreshToken()).orElseThrow();
        assertEquals(original.getExpiresAt(), rotated.getExpiresAt());
        assertNotEquals(original.getRefreshToken(), rotated.getRefreshToken());
        assertTrue(service.rotate(original.getRefreshToken()).isEmpty());
        assertNotNull(sessions.findById(original.getSessionId()).orElseThrow().getRevokedAt());
        assertTrue(service.rotate(rotated.getRefreshToken()).isEmpty());

        try (AnnotationConfigApplicationContext reopened = context(source, userId)) {
            AuthSessionService restarted = reopened.getBean(AuthSessionService.class);
            assertTrue(restarted.rotate(rotated.getRefreshToken()).isEmpty());
            assertTrue(restarted.rotate(independent.getRefreshToken()).isPresent());
        }

        Grant rollback = service.create(userId);
        assertThrows(IllegalStateException.class, () -> transaction.execute(status -> {
            service.rotate(rollback.getRefreshToken()).orElseThrow();
            throw new IllegalStateException("test rollback");
        }));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM tb_auth_refresh_token "
                + "WHERE session_id = ? AND consumed_at IS NOT NULL", Integer.class, rollback.getSessionId()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM tb_auth_refresh_token "
                + "WHERE session_id = ?", Integer.class, rollback.getSessionId()));
        assertTrue(service.rotate(rollback.getRefreshToken()).isPresent());

        Long before = jdbc.queryForObject("SELECT COUNT(*) FROM tb_auth_session WHERE user_id = ?", Long.class, userId);
        transaction.executeWithoutResult(status -> {
            service.create(userId);
            status.setRollbackOnly();
        });
        assertEquals(before, jdbc.queryForObject("SELECT COUNT(*) FROM tb_auth_session WHERE user_id = ?", Long.class, userId));

        for (int attempt = 0; attempt < 3; attempt++) {
            Grant concurrent = service.create(userId);
            List<Boolean> results = race(() -> service.rotate(concurrent.getRefreshToken()).isPresent(),
                    () -> service.rotate(concurrent.getRefreshToken()).isPresent());
            assertNotEquals(results.get(0), results.get(1));
            assertNotNull(sessions.findById(concurrent.getSessionId()).orElseThrow().getRevokedAt());
        }
        Grant logout = service.create(userId);
        List<Boolean> logoutResults = race(() -> service.rotate(logout.getRefreshToken()).isPresent(),
                () -> service.revoke(logout.getRefreshToken()));
        assertTrue(logoutResults.get(1));
        assertTrue(service.revoke(logout.getRefreshToken()));
        assertTrue(service.rotate(logout.getRefreshToken()).isEmpty());
        assertNotNull(sessions.findById(logout.getSessionId()).orElseThrow().getRevokedAt());

        Grant expired = service.create(userId);
        jdbc.update("UPDATE tb_auth_session SET expires_at = ? WHERE id = ?",
                LocalDateTime.now(ZoneOffset.UTC).minusDays(1), expired.getSessionId());
        assertTrue(service.rotate(expired.getRefreshToken()).isEmpty());
        assertTrue(service.rotate("0".repeat(64)).isEmpty());
        assertFalse(service.revoke("0".repeat(64)));

        Grant orphan = service.create(userId);
        when(context.getBean(UserDao.class).existsById(userId)).thenReturn(false);
        assertTrue(service.rotate(orphan.getRefreshToken()).isEmpty());
        assertNotNull(sessions.findById(orphan.getSessionId()).orElseThrow().getRevokedAt());
    }

    private List<Boolean> race(java.util.concurrent.Callable<Boolean> first,
            java.util.concurrent.Callable<Boolean> second) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (java.util.concurrent.Callable<Boolean> action : List.of(first, second)) {
                futures.add(executor.submit(() -> {
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    return action.call();
                }));
            }
            start.countDown();
            return List.of(futures.get(0).get(30, TimeUnit.SECONDS), futures.get(1).get(30, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
        }
    }

    private AnnotationConfigApplicationContext context(DataSource source, String userId) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(DataSource.class, () -> source);
        context.registerBean(UserDao.class, () -> {
            UserDao users = mock(UserDao.class);
            when(users.existsById(userId)).thenReturn(true);
            return users;
        });
        context.register(Persistence.class);
        try {
            context.refresh();
            return context;
        } catch (RuntimeException failure) {
            context.close();
            throw failure;
        }
    }

    private DataSource testDataSource() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new FileSystemResource(Path.of(System.getenv("HOW_AUTH_SERVICE_TEST_CONFIG"))));
        Properties settings = yaml.getObject();
        assertNotNull(settings);
        StandardEnvironment environment = new StandardEnvironment();
        DriverManagerDataSource source = new DriverManagerDataSource();
        source.setUrl(environment.resolveRequiredPlaceholders(settings.getProperty("spring.datasource.url")));
        source.setUsername(environment.resolveRequiredPlaceholders(settings.getProperty("spring.datasource.username")));
        source.setPassword(environment.resolveRequiredPlaceholders(settings.getProperty("spring.datasource.password")));
        Properties options = new Properties();
        options.setProperty("connectTimeout", "10000");
        options.setProperty("socketTimeout", "20000");
        options.setProperty("allowMultiQueries", "false");
        options.setProperty("connectionTimeZone", "UTC");
        options.setProperty("forceConnectionTimeZoneToSession", "true");
        source.setConnectionProperties(options);
        return source;
    }

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = AuthSessionDao.class,
            excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = UserDao.class))
    @Import(AuthSessionService.class)
    static class Persistence {
        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
            LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(source);
            factory.setPackagesToScan("com.liushao.user.pojo");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            Properties properties = new Properties();
            properties.setProperty("hibernate.hbm2ddl.auto", "none");
            properties.setProperty("hibernate.dialect", "org.hibernate.dialect.MySQL8Dialect");
            properties.setProperty("hibernate.jdbc.time_zone", "UTC");
            properties.setProperty("hibernate.show_sql", "false");
            factory.setJpaProperties(properties);
            return factory;
        }

        @Bean
        PlatformTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }
    }
}