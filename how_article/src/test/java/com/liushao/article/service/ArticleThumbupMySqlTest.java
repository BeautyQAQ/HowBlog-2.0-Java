package com.liushao.article.service;

import java.nio.file.Path;
import java.sql.Connection;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.liushao.article.dao.ArticleDao;
import com.liushao.util.IdWorker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 由 HOW_ARTICLE_TEST_CONFIG 显式启用的真实 MySQL 验收：验证文章点赞唯一关系与原子计数，
 * 覆盖首次、重复、同用户并发、多用户并发、文章不存在与非已发布。测试数据按主键精确清理，默认跳过。
 */
@EnabledIfEnvironmentVariable(named = "HOW_ARTICLE_TEST_CONFIG", matches = ".+")
class ArticleThumbupMySqlTest {

    @Test
    void verifiesArticleThumbupConsistencyAgainstRealMySql() {
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger)
                org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ch.qos.logback.classic.Level previousLevel = logger.getLevel();
        logger.setLevel(ch.qos.logback.classic.Level.OFF);
        String stage = "configuration";
        List<String> articleIds = new ArrayList<>();
        JdbcTemplate jdbc = null;
        try {
            Properties settings = readSettings();
            DataSource source = testDataSource(settings);
            stage = "database preconditions";
            try (Connection connection = source.getConnection()) {
                assertEquals("MySQL", connection.getMetaData().getDatabaseProductName());
                assertTrue(connection.getMetaData().getDatabaseMajorVersion() >= 8);
            }
            jdbc = new JdbcTemplate(source);
            stage = "thumbup table precondition";
            int thumbupTables = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables "
                    + "WHERE table_schema = DATABASE() AND table_name = 'tb_article_thumbup'", Integer.class);
            assertEquals(1, thumbupTables);
            try (AnnotationConfigApplicationContext context = context(source)) {
                ArticleService service = context.getBean(ArticleService.class);
                stage = "first and duplicate thumbup";
                verifyFirstAndDuplicate(service, jdbc, articleIds);
                stage = "concurrent same-user thumbup";
                verifyConcurrentSameUser(service, jdbc, articleIds);
                stage = "concurrent distinct-user thumbup";
                verifyConcurrentDistinctUsers(service, jdbc, articleIds);
                stage = "missing and unpublished article";
                verifyMissingAndUnpublished(service, jdbc, articleIds);
            }
            stage = "cleanup verification";
            cleanup(jdbc, articleIds);
            assertNoLeftovers(jdbc, articleIds);
        } catch (Throwable failure) {
            int line = java.util.Arrays.stream(failure.getStackTrace())
                    .filter(frame -> frame.getClassName().equals(getClass().getName()))
                    .mapToInt(StackTraceElement::getLineNumber).findFirst().orElse(-1);
            fail("Article thumbup storage verification failed at " + stage + " (details suppressed): "
                    + failure.getClass().getSimpleName() + "; test line=" + line);
        } finally {
            if (jdbc != null) {
                try {
                    cleanup(jdbc, articleIds);
                } catch (RuntimeException ignored) {
                    // 失败路径的兜底清理，清理结果由主流程断言负责
                }
            }
            logger.setLevel(previousLevel);
        }
    }

    private void verifyFirstAndDuplicate(ArticleService service, JdbcTemplate jdbc, List<String> articleIds) {
        String userId = testUser();
        String articleId = insertArticle(jdbc, articleIds, "1");

        assertEquals(ArticleService.ThumbupResult.SUCCESS, service.thumbup(articleId, userId));
        assertEquals(1, thumbupCount(jdbc, articleId));
        assertEquals(1, relationCount(jdbc, articleId, userId));

        assertEquals(ArticleService.ThumbupResult.DUPLICATE, service.thumbup(articleId, userId));
        assertEquals(1, thumbupCount(jdbc, articleId));
        assertEquals(1, relationCount(jdbc, articleId, userId));
    }

    private void verifyConcurrentSameUser(ArticleService service, JdbcTemplate jdbc, List<String> articleIds)
            throws Exception {
        String userId = testUser();
        String articleId = insertArticle(jdbc, articleIds, "1");

        List<ArticleService.ThumbupResult> results = race(service, articleId, Collections.nCopies(20, userId));
        long succeeded = results.stream().filter(result -> result == ArticleService.ThumbupResult.SUCCESS).count();
        long duplicates = results.stream().filter(result -> result == ArticleService.ThumbupResult.DUPLICATE).count();
        assertEquals(1, succeeded);
        assertEquals(19, duplicates);
        assertEquals(1, thumbupCount(jdbc, articleId));
        assertEquals(1, relationCount(jdbc, articleId, userId));
    }

    private void verifyConcurrentDistinctUsers(ArticleService service, JdbcTemplate jdbc, List<String> articleIds)
            throws Exception {
        String articleId = insertArticle(jdbc, articleIds, "1");
        List<String> userIds = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            userIds.add(testUser());
        }

        List<ArticleService.ThumbupResult> results = race(service, articleId, userIds);
        assertEquals(10, results.stream().filter(result -> result == ArticleService.ThumbupResult.SUCCESS).count());
        assertEquals(10, thumbupCount(jdbc, articleId));
        assertEquals(10, allRelationCount(jdbc, articleId));
    }

    private void verifyMissingAndUnpublished(ArticleService service, JdbcTemplate jdbc, List<String> articleIds) {
        String missingId = "thumbup-test-missing-" + UUID.randomUUID();
        assertEquals(ArticleService.ThumbupResult.NOT_FOUND, service.thumbup(missingId, testUser()));
        assertEquals(0, allRelationCount(jdbc, missingId));

        String draftId = insertArticle(jdbc, articleIds, "0");
        assertEquals(ArticleService.ThumbupResult.NOT_FOUND, service.thumbup(draftId, testUser()));
        assertEquals(0, thumbupCount(jdbc, draftId));
        assertEquals(0, allRelationCount(jdbc, draftId));
    }

    private List<ArticleService.ThumbupResult> race(ArticleService service, String articleId, List<String> userIds)
            throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(userIds.size());
        try {
            List<Future<ArticleService.ThumbupResult>> futures = new ArrayList<>();
            for (String userId : userIds) {
                futures.add(executor.submit(() -> {
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    return service.thumbup(articleId, userId);
                }));
            }
            start.countDown();
            List<ArticleService.ThumbupResult> results = new ArrayList<>();
            for (Future<ArticleService.ThumbupResult> future : futures) {
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

    private String insertArticle(JdbcTemplate jdbc, List<String> articleIds, String state) {
        String id = "thumbup-test-" + UUID.randomUUID();
        jdbc.update("INSERT INTO tb_article (id, userid, title, content, state, createtime, updatetime, visits, thumbup) "
                        + "VALUES (?, ?, ?, ?, ?, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0, 0)",
                id, "thumbup-test-author", "article thumbup integration fixture", "body", state);
        articleIds.add(id);
        return id;
    }

    private int thumbupCount(JdbcTemplate jdbc, String articleId) {
        return jdbc.queryForObject("SELECT thumbup FROM tb_article WHERE id = ?", Integer.class, articleId);
    }

    private int relationCount(JdbcTemplate jdbc, String articleId, String userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM tb_article_thumbup WHERE articleid = ? AND userid = ?",
                Integer.class, articleId, userId);
    }

    private int allRelationCount(JdbcTemplate jdbc, String articleId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM tb_article_thumbup WHERE articleid = ?",
                Integer.class, articleId);
    }

    private void cleanup(JdbcTemplate jdbc, List<String> articleIds) {
        for (String articleId : articleIds) {
            jdbc.update("DELETE FROM tb_article_thumbup WHERE articleid = ?", articleId);
            jdbc.update("DELETE FROM tb_article WHERE id = ?", articleId);
        }
    }

    private void assertNoLeftovers(JdbcTemplate jdbc, List<String> articleIds) {
        for (String articleId : articleIds) {
            assertEquals(0, allRelationCount(jdbc, articleId));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM tb_article WHERE id = ?",
                    Integer.class, articleId));
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

    private AnnotationConfigApplicationContext context(DataSource source) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(DataSource.class, () -> source);
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
    @EnableJpaRepositories(basePackageClasses = ArticleDao.class)
    @Import(ArticleService.class)
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
