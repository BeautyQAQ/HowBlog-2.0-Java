package com.liushao.article.service;

import java.nio.file.Path;
import java.sql.Connection;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
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
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.liushao.article.dao.CommentDao;
import com.liushao.util.IdWorker;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "HOW_ARTICLE_TEST_CONFIG", matches = ".+")
class CommentThumbupMySqlRedisTest {
    private static final String LEGACY_PREFIX = "thumbup_";
    private static final String CACHE_PREFIX = "thumbup:v2:";

    @Test
    void verifiesThumbupConsistencyAgainstRealMySqlAndRedis() {
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger)
                org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ch.qos.logback.classic.Level previousLevel = logger.getLevel();
        logger.setLevel(ch.qos.logback.classic.Level.OFF);
        String stage = "configuration";
        List<String> commentIds = new ArrayList<>();
        List<String> redisKeys = new ArrayList<>();
        JdbcTemplate jdbc = null;
        RedisTemplate<Object, Object> redis = null;
        LettuceConnectionFactory redisConnection = null;
        try {
            Properties settings = readSettings();
            DataSource source = testDataSource(settings);
            stage = "database preconditions";
            try (Connection connection = source.getConnection()) {
                assertEquals("MySQL", connection.getMetaData().getDatabaseProductName());
                assertTrue(connection.getMetaData().getDatabaseMajorVersion() >= 8);
                assertEquals(Connection.TRANSACTION_REPEATABLE_READ, connection.getTransactionIsolation());
            }
            jdbc = new JdbcTemplate(source);
            stage = "thumbup table precondition";
            int thumbupTables = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables "
                    + "WHERE table_schema = DATABASE() AND table_name = 'tb_comment_thumbup'", Integer.class);
            assertEquals(1, thumbupTables);
            redisConnection = redisConnection(settings);
            stage = "redis precondition";
            try (RedisConnection connection = redisConnection.getConnection()) {
                assertEquals("PONG", connection.ping());
            }
            redis = redisTemplate(redisConnection);
            try (AnnotationConfigApplicationContext context = context(source, redis)) {
                CommentService service = context.getBean(CommentService.class);
                stage = "first and duplicate thumbup";
                verifyFirstAndDuplicate(service, jdbc, redis, commentIds, redisKeys);
                stage = "legacy key migration";
                verifyLegacyMigration(service, jdbc, redis, commentIds, redisKeys);
                stage = "concurrent same-user thumbup";
                verifyConcurrentSameUser(service, jdbc, redis, commentIds, redisKeys);
                stage = "concurrent distinct-user thumbup";
                verifyConcurrentDistinctUsers(service, jdbc, redis, commentIds, redisKeys);
                stage = "missing comment";
                verifyMissingComment(service, jdbc);
            }
            stage = "cleanup verification";
            cleanupStorage(jdbc, redis, commentIds, redisKeys);
            assertNoLeftovers(jdbc, redis, commentIds, redisKeys);
        } catch (Throwable failure) {
            int line = java.util.Arrays.stream(failure.getStackTrace())
                    .filter(frame -> frame.getClassName().equals(getClass().getName()))
                    .mapToInt(StackTraceElement::getLineNumber).findFirst().orElse(-1);
            fail("Thumbup storage verification failed at " + stage + " (details suppressed): "
                    + failure.getClass().getSimpleName() + "; test line=" + line);
        } finally {
            if (jdbc != null && redis != null) {
                try {
                    cleanupStorage(jdbc, redis, commentIds, redisKeys);
                } catch (RuntimeException ignored) {
                    // 失败路径的兜底清理，清理结果由主流程断言负责
                }
            }
            if (redisConnection != null) redisConnection.destroy();
            logger.setLevel(previousLevel);
        }
    }

    private void verifyFirstAndDuplicate(CommentService service, JdbcTemplate jdbc, RedisTemplate<Object, Object> redis,
            List<String> commentIds, List<String> redisKeys) {
        String userId = testUser();
        String commentId = insertComment(jdbc, commentIds);
        String cacheKey = CACHE_PREFIX + userId + ":" + commentId;
        String legacyKey = LEGACY_PREFIX + userId + "_" + commentId;
        redisKeys.add(cacheKey);
        redisKeys.add(legacyKey);

        assertEquals(CommentService.ThumbupResult.SUCCESS, service.thumbup(commentId, userId));
        assertEquals(1, thumbupCount(jdbc, commentId));
        assertEquals(1, relationCount(jdbc, commentId, userId));
        assertTrue(Boolean.TRUE.equals(redis.hasKey(cacheKey)));
        assertFalse(Boolean.TRUE.equals(redis.hasKey(legacyKey)));

        assertEquals(CommentService.ThumbupResult.DUPLICATE, service.thumbup(commentId, userId));
        assertEquals(1, thumbupCount(jdbc, commentId));
        assertEquals(1, relationCount(jdbc, commentId, userId));
    }

    private void verifyLegacyMigration(CommentService service, JdbcTemplate jdbc, RedisTemplate<Object, Object> redis,
            List<String> commentIds, List<String> redisKeys) {
        String userId = testUser();
        String commentId = insertComment(jdbc, commentIds);
        String legacyKey = LEGACY_PREFIX + userId + "_" + commentId;
        String cacheKey = CACHE_PREFIX + userId + ":" + commentId;
        redisKeys.add(legacyKey);
        redisKeys.add(cacheKey);
        redis.opsForValue().set(legacyKey, 1, Duration.ofHours(1));

        assertEquals(CommentService.ThumbupResult.DUPLICATE, service.thumbup(commentId, userId));
        assertEquals(0, thumbupCount(jdbc, commentId));
        assertEquals(1, relationCount(jdbc, commentId, userId));
        assertTrue(Boolean.TRUE.equals(redis.hasKey(cacheKey)));
    }

    private void verifyConcurrentSameUser(CommentService service, JdbcTemplate jdbc, RedisTemplate<Object, Object> redis,
            List<String> commentIds, List<String> redisKeys) throws Exception {
        String userId = testUser();
        String commentId = insertComment(jdbc, commentIds);
        redisKeys.add(CACHE_PREFIX + userId + ":" + commentId);
        redisKeys.add(LEGACY_PREFIX + userId + "_" + commentId);

        List<CommentService.ThumbupResult> results = race(service, commentId, Collections.nCopies(20, userId));
        long succeeded = results.stream().filter(result -> result == CommentService.ThumbupResult.SUCCESS).count();
        long duplicates = results.stream().filter(result -> result == CommentService.ThumbupResult.DUPLICATE).count();
        assertEquals(1, succeeded);
        assertEquals(19, duplicates);
        assertEquals(1, thumbupCount(jdbc, commentId));
        assertEquals(1, relationCount(jdbc, commentId, userId));
    }

    private void verifyConcurrentDistinctUsers(CommentService service, JdbcTemplate jdbc,
            RedisTemplate<Object, Object> redis, List<String> commentIds, List<String> redisKeys) throws Exception {
        String commentId = insertComment(jdbc, commentIds);
        List<String> userIds = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            String userId = testUser();
            userIds.add(userId);
            redisKeys.add(CACHE_PREFIX + userId + ":" + commentId);
            redisKeys.add(LEGACY_PREFIX + userId + "_" + commentId);
        }

        List<CommentService.ThumbupResult> results = race(service, commentId, userIds);
        assertEquals(10, results.stream().filter(result -> result == CommentService.ThumbupResult.SUCCESS).count());
        assertEquals(10, thumbupCount(jdbc, commentId));
        assertEquals(10, allRelationCount(jdbc, commentId));
    }

    private void verifyMissingComment(CommentService service, JdbcTemplate jdbc) {
        String commentId = "thumbup-test-missing-" + UUID.randomUUID();
        assertEquals(CommentService.ThumbupResult.NOT_FOUND, service.thumbup(commentId, testUser()));
        assertEquals(0, allRelationCount(jdbc, commentId));
    }

    private List<CommentService.ThumbupResult> race(CommentService service, String commentId, List<String> userIds)
            throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(userIds.size());
        try {
            List<Future<CommentService.ThumbupResult>> futures = new ArrayList<>();
            for (String userId : userIds) {
                futures.add(executor.submit(() -> {
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    return service.thumbup(commentId, userId);
                }));
            }
            start.countDown();
            List<CommentService.ThumbupResult> results = new ArrayList<>();
            for (Future<CommentService.ThumbupResult> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
        }
    }

    private String testUser() {
        return "thumbup-test-user-" + UUID.randomUUID();
    }

    private String insertComment(JdbcTemplate jdbc, List<String> commentIds) {
        String id = "thumbup-test-" + UUID.randomUUID();
        jdbc.update("INSERT INTO tb_comment (id, articleid, content, userid, publishdate, thumbup) "
                        + "VALUES (?, ?, ?, ?, UTC_TIMESTAMP(), 0)",
                id, "thumbup-test-article", "thumbup integration test fixture", "thumbup-test-author");
        commentIds.add(id);
        return id;
    }

    private int thumbupCount(JdbcTemplate jdbc, String commentId) {
        return jdbc.queryForObject("SELECT thumbup FROM tb_comment WHERE id = ?", Integer.class, commentId);
    }

    private int relationCount(JdbcTemplate jdbc, String commentId, String userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM tb_comment_thumbup WHERE commentid = ? AND userid = ?",
                Integer.class, commentId, userId);
    }

    private int allRelationCount(JdbcTemplate jdbc, String commentId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM tb_comment_thumbup WHERE commentid = ?",
                Integer.class, commentId);
    }

    private void cleanupStorage(JdbcTemplate jdbc, RedisTemplate<Object, Object> redis, List<String> commentIds,
            List<String> redisKeys) {
        for (String commentId : commentIds) {
            jdbc.update("DELETE FROM tb_comment_thumbup WHERE commentid = ?", commentId);
            jdbc.update("DELETE FROM tb_comment WHERE id = ?", commentId);
        }
        for (String key : redisKeys) {
            redis.delete(key);
        }
    }

    private void assertNoLeftovers(JdbcTemplate jdbc, RedisTemplate<Object, Object> redis, List<String> commentIds,
            List<String> redisKeys) {
        for (String commentId : commentIds) {
            assertEquals(0, allRelationCount(jdbc, commentId));
            int remaining = jdbc.queryForObject("SELECT COUNT(*) FROM tb_comment WHERE id = ?",
                    Integer.class, commentId);
            assertEquals(0, remaining);
        }
        for (String key : redisKeys) {
            assertFalse(Boolean.TRUE.equals(redis.hasKey(key)));
        }
    }

    private Properties readSettings() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new FileSystemResource(Path.of(System.getenv("HOW_ARTICLE_TEST_CONFIG")).toAbsolutePath()));
        Properties settings = yaml.getObject();
        assertNotNull(settings);
        return settings;
    }

    private DataSource testDataSource(Properties settings) {
        StandardEnvironment environment = new StandardEnvironment();
        DriverManagerDataSource source = new DriverManagerDataSource();
        source.setUrl(environment.resolveRequiredPlaceholders(settings.getProperty("spring.datasource.url")));
        source.setUsername(environment.resolveRequiredPlaceholders(settings.getProperty("spring.datasource.username")));
        source.setPassword(environment.resolveRequiredPlaceholders(settings.getProperty("spring.datasource.password")));
        Properties options = new Properties();
        options.setProperty("connectTimeout", "10000");
        options.setProperty("socketTimeout", "20000");
        options.setProperty("allowMultiQueries", "false");
        source.setConnectionProperties(options);
        return source;
    }

    private LettuceConnectionFactory redisConnection(Properties settings) {
        StandardEnvironment environment = new StandardEnvironment();
        RedisStandaloneConfiguration standalone = new RedisStandaloneConfiguration(
                environment.resolveRequiredPlaceholders(settings.getProperty("spring.redis.host", "127.0.0.1")),
                Integer.parseInt(environment.resolveRequiredPlaceholders(
                        settings.getProperty("spring.redis.port", "6379"))));
        String password = environment.resolveRequiredPlaceholders(settings.getProperty("spring.redis.password", ""));
        if (!password.isEmpty()) {
            standalone.setPassword(RedisPassword.of(password));
        }
        standalone.setDatabase(Integer.parseInt(environment.resolveRequiredPlaceholders(
                settings.getProperty("spring.redis.database", "0"))));
        LettuceConnectionFactory connection = new LettuceConnectionFactory(standalone,
                LettuceClientConfiguration.builder().commandTimeout(Duration.ofSeconds(2)).build());
        connection.afterPropertiesSet();
        return connection;
    }

    private RedisTemplate<Object, Object> redisTemplate(LettuceConnectionFactory connection) {
        RedisTemplate<Object, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connection);
        template.afterPropertiesSet();
        return template;
    }

    private AnnotationConfigApplicationContext context(DataSource source, RedisTemplate<Object, Object> redis) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(DataSource.class, () -> source);
        context.registerBean(RedisTemplate.class, () -> redis);
        context.registerBean(IdWorker.class, () -> new IdWorker(1, 1));
        context.register(Persistence.class);
        try {
            context.refresh();
            return context;
        } catch (RuntimeException failure) {
            context.close();
            throw failure;
        }
    }

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = CommentDao.class)
    @Import(CommentService.class)
    static class Persistence {
        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
            LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(source);
            factory.setPackagesToScan("com.liushao.article.pojo");
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
