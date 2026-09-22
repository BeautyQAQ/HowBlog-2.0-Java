package com.liushao.user.auth;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@EnabledIfEnvironmentVariable(named = "HOW_AUTH_TEST_CONFIG", matches = ".+")
class AuthSchemaIntegrationTest {
    @Test
    void verifiesAdditiveSchemaRollbackAndSerializedRefresh() {
        ch.qos.logback.classic.Logger rootLogger = (ch.qos.logback.classic.Logger)
                org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ch.qos.logback.classic.Level previousLevel = rootLogger.getLevel();
        rootLogger.setLevel(ch.qos.logback.classic.Level.WARN);
        try {
            verifyDatabase();
        } catch (Exception exception) {
            fail("Isolated authentication database verification failed (details suppressed): "
                    + exception.getClass().getSimpleName());
        } finally {
            rootLogger.setLevel(previousLevel);
        }
    }

    private void verifyDatabase() throws Exception {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        Path config = Path.of(System.getenv("HOW_AUTH_TEST_CONFIG")).toAbsolutePath();
        yaml.setResources(new FileSystemResource(config));
        Properties settings = yaml.getObject();
        assertNotNull(settings);
        StandardEnvironment environment = new StandardEnvironment();
        String url = environment.resolveRequiredPlaceholders(settings.getProperty("spring.datasource.url"));
        Properties credentials = new Properties();
        credentials.setProperty("user", environment.resolveRequiredPlaceholders(settings.getProperty("spring.datasource.username")));
        credentials.setProperty("password", environment.resolveRequiredPlaceholders(settings.getProperty("spring.datasource.password")));
        credentials.setProperty("connectTimeout", "10000");
        credentials.setProperty("socketTimeout", "15000");
        credentials.setProperty("allowMultiQueries", "false");
        String sessionId = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        String userId = "auth-test-" + UUID.randomUUID();
        byte[] tokenHash = java.security.MessageDigest.getInstance("SHA-256")
                .digest(UUID.randomUUID().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));

        try (Connection connection = DriverManager.getConnection(url, credentials)) {
            assertEquals("MySQL", connection.getMetaData().getDatabaseProductName());
            assertTrue(connection.getMetaData().getDatabaseMajorVersion() >= 8);
            Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath().getParent();
            FileSystemResource migration = new FileSystemResource(root.resolve("docs/mysql-auth-migration.sql"));
            ScriptUtils.executeSqlScript(connection, migration);
            ScriptUtils.executeSqlScript(connection, migration);
            connection.setAutoCommit(false);
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO tb_user_role (user_id, role) VALUES (?, 'ADMIN')")) {
                insert.setString(1, userId);
                assertEquals(1, insert.executeUpdate());
                connection.rollback();
            }
            try (PreparedStatement query = connection.prepareStatement("SELECT COUNT(*) FROM tb_user_role WHERE user_id = ?")) {
                query.setString(1, userId);
                try (ResultSet rows = query.executeQuery()) {
                    assertTrue(rows.next());
                    assertEquals(0, rows.getInt(1));
                }
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO tb_auth_session (id, user_id, created_at, expires_at) VALUES (?, ?, UTC_TIMESTAMP(6), DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 10 MINUTE))")) {
                insert.setString(1, sessionId);
                insert.setString(2, userId);
                assertEquals(1, insert.executeUpdate());
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO tb_auth_refresh_token (token_hash, session_id, created_at) VALUES (?, ?, UTC_TIMESTAMP(6))")) {
                insert.setBytes(1, tokenHash);
                insert.setString(2, sessionId);
                assertEquals(1, insert.executeUpdate());
            }
            connection.commit();
            CountDownLatch firstLocked = new CountDownLatch(1);
            CountDownLatch secondAttempting = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<Integer> first = executor.submit(() -> consume(url, credentials, sessionId, tokenHash,
                        firstLocked, secondAttempting, true));
                Future<Integer> second = executor.submit(() -> consume(url, credentials, sessionId, tokenHash,
                        firstLocked, secondAttempting, false));
                assertEquals(1, first.get(30, TimeUnit.SECONDS));
                assertEquals(0, second.get(30, TimeUnit.SECONDS));
            } finally {
                executor.shutdownNow();
                assertTrue(executor.awaitTermination(20, TimeUnit.SECONDS));
                try (PreparedStatement revoke = connection.prepareStatement(
                        "UPDATE tb_auth_session SET revoked_at = UTC_TIMESTAMP(6), expires_at = UTC_TIMESTAMP(6) WHERE id = ? AND user_id = ?")) {
                    revoke.setString(1, sessionId);
                    revoke.setString(2, userId);
                    revoke.executeUpdate();
                    connection.commit();
                }
            }
        }
    }

    private int consume(String url, Properties credentials, String sessionId, byte[] tokenHash,
            CountDownLatch firstLocked, CountDownLatch secondAttempting, boolean first) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, credentials)) {
            connection.setAutoCommit(false);
            try {
                if (!first) {
                    assertTrue(firstLocked.await(Duration.ofSeconds(10).toMillis(), TimeUnit.MILLISECONDS));
                    secondAttempting.countDown();
                }
                try (PreparedStatement lock = connection.prepareStatement("SELECT id FROM tb_auth_session WHERE id = ? FOR UPDATE")) {
                    lock.setQueryTimeout(15);
                    lock.setString(1, sessionId);
                    try (ResultSet rows = lock.executeQuery()) {
                        assertTrue(rows.next());
                    }
                }
                if (first) {
                    firstLocked.countDown();
                    assertTrue(secondAttempting.await(10, TimeUnit.SECONDS));
                }
                int updated;
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE tb_auth_refresh_token SET consumed_at = UTC_TIMESTAMP(6) WHERE token_hash = ? AND session_id = ? AND consumed_at IS NULL")) {
                    update.setBytes(1, tokenHash);
                    update.setString(2, sessionId);
                    updated = update.executeUpdate();
                }
                connection.commit();
                return updated;
            } catch (Exception exception) {
                connection.rollback();
                throw new SQLException("Authentication transaction check failed");
            }
        }
    }
}