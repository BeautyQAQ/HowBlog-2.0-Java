package com.liushao.article.service;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

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
import com.liushao.article.pojo.Article;
import com.liushao.util.IdWorker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 由 HOW_ARTICLE_TEST_CONFIG 显式启用的真实 MySQL 验收：验证文章草稿/发布/下架状态与
 * 发布时间在真实数据库上的落库行为。测试数据按主键精确清理，默认跳过。
 */
@EnabledIfEnvironmentVariable(named = "HOW_ARTICLE_TEST_CONFIG", matches = ".+")
class ArticleLifecycleMySqlTest {
    private static final String AUTHOR = "article-lifecycle-test-author";

    @Test
    void verifiesDraftPublishLifecycleAgainstRealMySql() {
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
            try (AnnotationConfigApplicationContext context = context(source)) {
                ArticleService service = context.getBean(ArticleService.class);

                stage = "draft creation";
                Article article = new Article();
                article.setTitle("article-lifecycle-test");
                article.setContent("article lifecycle integration fixture");
                article.setState(Article.STATE_PUBLISHED);
                article.setVisits(99);
                service.add(article, AUTHOR);
                articleIds.add(article.getId());
                assertEquals(Article.STATE_DRAFT, storedState(jdbc, article.getId()));
                assertNull(storedCreatetime(jdbc, article.getId()));
                assertEquals(0, storedVisits(jdbc, article.getId()));

                stage = "publish";
                assertTrue(service.publish(article.getId(), AUTHOR));
                assertEquals(Article.STATE_PUBLISHED, storedState(jdbc, article.getId()));
                Timestamp firstPublish = storedCreatetime(jdbc, article.getId());
                assertNotNull(firstPublish);

                stage = "republish keeps first publish time";
                assertTrue(service.publish(article.getId(), AUTHOR));
                assertEquals(firstPublish, storedCreatetime(jdbc, article.getId()));

                stage = "unpublish";
                assertTrue(service.unpublish(article.getId(), AUTHOR));
                assertEquals(Article.STATE_DRAFT, storedState(jdbc, article.getId()));
                assertEquals(firstPublish, storedCreatetime(jdbc, article.getId()));

                stage = "non-author publish";
                assertFalse(service.publish(article.getId(), "someone-else"));
                assertEquals(Article.STATE_DRAFT, storedState(jdbc, article.getId()));
            }
            stage = "cleanup verification";
            cleanup(jdbc, articleIds);
            assertNoLeftovers(jdbc, articleIds);
        } catch (Throwable failure) {
            int line = java.util.Arrays.stream(failure.getStackTrace())
                    .filter(frame -> frame.getClassName().equals(getClass().getName()))
                    .mapToInt(StackTraceElement::getLineNumber).findFirst().orElse(-1);
            fail("Article lifecycle storage verification failed at " + stage + " (details suppressed): "
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

    private String storedState(JdbcTemplate jdbc, String id) {
        return jdbc.queryForObject("SELECT state FROM tb_article WHERE id = ?", String.class, id);
    }

    private Timestamp storedCreatetime(JdbcTemplate jdbc, String id) {
        return jdbc.queryForObject("SELECT createtime FROM tb_article WHERE id = ?", Timestamp.class, id);
    }

    private int storedVisits(JdbcTemplate jdbc, String id) {
        return jdbc.queryForObject("SELECT visits FROM tb_article WHERE id = ?", Integer.class, id);
    }

    private void cleanup(JdbcTemplate jdbc, List<String> articleIds) {
        for (String articleId : articleIds) {
            jdbc.update("DELETE FROM tb_article WHERE id = ?", articleId);
        }
    }

    private void assertNoLeftovers(JdbcTemplate jdbc, List<String> articleIds) {
        for (String articleId : articleIds) {
            int remaining = jdbc.queryForObject("SELECT COUNT(*) FROM tb_article WHERE id = ?",
                    Integer.class, articleId);
            assertEquals(0, remaining);
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
