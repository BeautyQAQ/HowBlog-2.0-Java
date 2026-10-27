package com.liushao.article.service;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.liushao.article.dao.ArticleDao;
import com.liushao.article.pojo.Article;
import com.liushao.article.pojo.Comment;
import com.liushao.util.IdWorker;
import com.liushao.web.ForbiddenException;
import com.liushao.web.ResourceNotFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;

/**
 * 由 HOW_ARTICLE_TEST_CONFIG 显式启用的真实 MySQL 验收：验证文章草稿/发布/审核状态与
 * 发布时间在真实数据库上的落库行为。测试数据（文章、临时管理员）按主键精确清理，默认跳过。
 */
@EnabledIfEnvironmentVariable(named = "HOW_ARTICLE_TEST_CONFIG", matches = ".+")
class ArticleLifecycleMySqlTest {
    private static final String AUTHOR = "article-lifecycle-test-author";

    @Test
    void verifiesDraftPublishReviewLifecycleAgainstRealMySql() {
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger)
                org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ch.qos.logback.classic.Level previousLevel = logger.getLevel();
        logger.setLevel(ch.qos.logback.classic.Level.OFF);
        String stage = "configuration";
        List<String> articleIds = new ArrayList<>();
        List<String> adminIds = new ArrayList<>();
        List<String> labelIds = new ArrayList<>();
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
                String labelPrefix = "article-label-test-" + UUID.randomUUID().toString().replace("-", "");
                String firstLabelId = labelPrefix + "-a";
                String secondLabelId = labelPrefix + "-b";
                insertLabel(jdbc, firstLabelId, "article-label-test-a");
                insertLabel(jdbc, secondLabelId, "article-label-test-b");
                labelIds.add(firstLabelId);
                labelIds.add(secondLabelId);
                article.setLabelIds(List.of(firstLabelId, secondLabelId));
                service.add(article, AUTHOR);
                articleIds.add(article.getId());
                assertEquals(Article.STATE_DRAFT, storedState(jdbc, article.getId()));
                assertNull(storedCreatetime(jdbc, article.getId()));
                assertEquals(0, storedVisits(jdbc, article.getId()));
                assertEquals(List.of(firstLabelId, secondLabelId), storedLabelIds(jdbc, article.getId()));
                assertEquals(1L, storedLabelCount(jdbc, firstLabelId));
                assertEquals(1L, storedLabelCount(jdbc, secondLabelId));
                stage = "search article by all labels";
                assertEquals(1, service.search(Map.of("labelIds", List.of(firstLabelId, secondLabelId)),
                    1, 10, AUTHOR).getTotalElements());

                stage = "replace article labels";
                Article labelUpdate = new Article();
                labelUpdate.setId(article.getId());
                labelUpdate.setLabelIds(List.of(secondLabelId));
                assertTrue(service.update(labelUpdate, AUTHOR));
                assertEquals(List.of(secondLabelId), storedLabelIds(jdbc, article.getId()));
                assertEquals(0L, storedLabelCount(jdbc, firstLabelId));
                assertEquals(1L, storedLabelCount(jdbc, secondLabelId));
                assertEquals(1, service.search(Map.of("labelIds", List.of(secondLabelId)),
                    1, 10, AUTHOR).getTotalElements());
                assertEquals(0, service.search(Map.of("labelIds", List.of(firstLabelId, secondLabelId)),
                    1, 10, AUTHOR).getTotalElements());

                stage = "publish";
                assertTrue(service.publish(article.getId(), AUTHOR));
                assertEquals(Article.STATE_PUBLISHED, storedState(jdbc, article.getId()));
                Timestamp firstPublish = storedCreatetime(jdbc, article.getId());
                assertNotNull(firstPublish);

                stage = "published reads count visits";
                service.findById(article.getId(), null);
                assertEquals(1, storedVisits(jdbc, article.getId()));
                service.findById(article.getId(), AUTHOR);
                assertEquals(1, storedVisits(jdbc, article.getId()));
                service.findById(article.getId(), "someone-else");
                assertEquals(2, storedVisits(jdbc, article.getId()));

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

                stage = "submit for review";
                assertEquals(ArticleService.TransitionResult.SUCCESS, service.submit(article.getId(), AUTHOR));
                assertEquals(Article.STATE_PENDING, storedState(jdbc, article.getId()));

                stage = "non-administrator review is rejected";
                assertThrows(ForbiddenException.class, () -> service.approve(article.getId(), AUTHOR));
                assertEquals(Article.STATE_PENDING, storedState(jdbc, article.getId()));

                stage = "administrator approval";
                String adminId = "alt-admin-" + UUID.randomUUID();
                insertAdministrator(jdbc, adminId);
                adminIds.add(adminId);
                assertEquals(ArticleService.TransitionResult.SUCCESS, service.approve(article.getId(), adminId));
                assertEquals(Article.STATE_PUBLISHED, storedState(jdbc, article.getId()));
                assertEquals(firstPublish, storedCreatetime(jdbc, article.getId()));

                stage = "administrator rejection returns article to draft";
                assertTrue(service.unpublish(article.getId(), AUTHOR));
                assertEquals(ArticleService.TransitionResult.SUCCESS, service.submit(article.getId(), AUTHOR));
                assertEquals(ArticleService.TransitionResult.SUCCESS, service.reject(article.getId(), adminId));
                assertEquals(Article.STATE_DRAFT, storedState(jdbc, article.getId()));

                stage = "private article and comment visibility";
                assertTrue(service.publish(article.getId(), AUTHOR));
                Article privateUpdate = new Article();
                privateUpdate.setId(article.getId());
                privateUpdate.setIspublic("0");
                assertTrue(service.update(privateUpdate, AUTHOR));
                assertThrows(ResourceNotFoundException.class, () -> service.findById(article.getId(), null));
                assertThrows(ResourceNotFoundException.class, () -> service.findById(article.getId(), "outsider"));
                assertEquals(0, service.search(Map.of("id", article.getId()), 1, 10, null).getTotalElements());
                assertEquals(1, service.search(Map.of("id", article.getId()), 1, 10, AUTHOR).getTotalElements());
                assertEquals(1, service.search(Map.of("id", article.getId()), 1, 10, adminId).getTotalElements());
                assertTrue(service.findAll().stream().noneMatch(a -> a.getId().equals(article.getId())));
                CommentService comments = context.getBean(CommentService.class);
                Comment root = comment(article.getId(), null);
                comments.save(root, AUTHOR);
                Comment reply = comment(article.getId(), root.get_id());
                comments.save(reply, AUTHOR);
                assertThrows(ResourceNotFoundException.class, () -> comments.findById(root.get_id(), null));
                assertThrows(ResourceNotFoundException.class, () -> comments.findByarticleId(article.getId(), "outsider"));
                assertThrows(ResourceNotFoundException.class, () -> comments.save(comment(article.getId(), null), "outsider"));
                assertEquals(CommentService.ThumbupResult.NOT_FOUND, comments.thumbup(root.get_id(), "outsider"));
                assertTrue(comments.deleteById(root.get_id(), AUTHOR));
                assertEquals(0, commentCount(jdbc, article.getId()));

                stage = "concurrent comments and article updates";
                privateUpdate.setIspublic("1");
                assertTrue(service.update(privateUpdate, AUTHOR));
                race(12, () -> { comments.save(comment(article.getId(), null), "reader"); return null; });
                assertEquals(12, commentCount(jdbc, article.getId()));
                assertEquals(12, comments.findByarticleId(article.getId(), null).size());
                int visitsBefore = storedVisits(jdbc, article.getId());
                race(8, () -> {
                    service.findById(article.getId(), "reader");
                    Article edit = new Article();
                    edit.setId(article.getId());
                    edit.setTitle("article-lifecycle-test-edited");
                    assertTrue(service.update(edit, AUTHOR));
                    return null;
                });
                assertEquals(visitsBefore + 8, storedVisits(jdbc, article.getId()));
                assertEquals(12, commentCount(jdbc, article.getId()));

                stage = "article deletion cleans comments and labels";
                assertTrue(service.delete(article.getId(), AUTHOR));
                assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM tb_comment WHERE articleid=?", Integer.class, article.getId()));
                assertEquals(0L, storedLabelCount(jdbc, secondLabelId));
            }
            stage = "cleanup verification";
            cleanup(jdbc, articleIds, adminIds, labelIds);
            assertNoLeftovers(jdbc, articleIds, adminIds, labelIds);
        } catch (Throwable failure) {
            int line = java.util.Arrays.stream(failure.getStackTrace())
                    .filter(frame -> frame.getClassName().equals(getClass().getName()))
                    .mapToInt(StackTraceElement::getLineNumber).findFirst().orElse(-1);
            fail("Article lifecycle storage verification failed at " + stage + " (details suppressed): "
                    + failure.getClass().getSimpleName() + "; test line=" + line);
        } finally {
            if (jdbc != null) {
                try {
                    cleanup(jdbc, articleIds, adminIds, labelIds);
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

    private Comment comment(String articleId, String parentId) {
        Comment comment = new Comment();
        comment.setArticleid(articleId);
        comment.setParentid(parentId);
        comment.setContent("article-lifecycle-test-comment");
        return comment;
    }

    private int commentCount(JdbcTemplate jdbc, String articleId) {
        return jdbc.queryForObject("SELECT comment FROM tb_article WHERE id=?", Integer.class, articleId);
    }

    private void race(int workers, Callable<Void> work) throws Exception {
        var executor = Executors.newFixedThreadPool(workers);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Void>> futures = new ArrayList<>();
            for (int i = 0; i < workers; i++) {
                futures.add(executor.submit(() -> {
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    return work.call();
                }));
            }
            start.countDown();
            for (Future<Void> future : futures) future.get(20, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private Timestamp storedCreatetime(JdbcTemplate jdbc, String id) {
        return jdbc.queryForObject("SELECT createtime FROM tb_article WHERE id = ?", Timestamp.class, id);
    }

    private int storedVisits(JdbcTemplate jdbc, String id) {
        return jdbc.queryForObject("SELECT visits FROM tb_article WHERE id = ?", Integer.class, id);
    }

    private List<String> storedLabelIds(JdbcTemplate jdbc, String articleId) {
        return jdbc.queryForList("SELECT labelid FROM tb_article_label WHERE articleid = ? ORDER BY labelid",
                String.class, articleId);
    }

    private long storedLabelCount(JdbcTemplate jdbc, String labelId) {
        return jdbc.queryForObject("SELECT count FROM tb_label WHERE id = ?", Long.class, labelId);
    }

    private void insertLabel(JdbcTemplate jdbc, String labelId, String name) {
        jdbc.update("INSERT INTO tb_label (id, labelname, count) VALUES (?, ?, 0)", labelId, name);
    }

    private void insertAdministrator(JdbcTemplate jdbc, String userId) {
        String mobile = "alt" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        jdbc.update("INSERT INTO tb_user (id, mobile, nickname) VALUES (?, ?, ?)",
                userId, mobile, "article-lifecycle-test-admin");
        jdbc.update("INSERT INTO tb_user_role (user_id, role) VALUES (?, 'ADMIN')", userId);
    }

    private void cleanup(JdbcTemplate jdbc, List<String> articleIds, List<String> adminIds,
                         List<String> labelIds) {
        for (String articleId : articleIds) {
            jdbc.update("DELETE FROM tb_comment_thumbup WHERE commentid IN (SELECT id FROM tb_comment WHERE articleid=?)", articleId);
            jdbc.update("DELETE FROM tb_comment WHERE articleid=?", articleId);
            jdbc.update("DELETE FROM tb_article WHERE id = ?", articleId);
        }
        for (String adminId : adminIds) {
            jdbc.update("DELETE FROM tb_user_role WHERE user_id = ?", adminId);
            jdbc.update("DELETE FROM tb_user WHERE id = ?", adminId);
        }
        for (String labelId : labelIds) {
            jdbc.update("DELETE FROM tb_label WHERE id = ?", labelId);
        }
    }

    private void assertNoLeftovers(JdbcTemplate jdbc, List<String> articleIds, List<String> adminIds,
                                   List<String> labelIds) {
        for (String articleId : articleIds) {
            int remaining = jdbc.queryForObject("SELECT COUNT(*) FROM tb_article WHERE id = ?",
                    Integer.class, articleId);
            assertEquals(0, remaining);
        }
        for (String adminId : adminIds) {
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM tb_user_role WHERE user_id = ?",
                    Integer.class, adminId));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM tb_user WHERE id = ?",
                    Integer.class, adminId));
        }
            for (String labelId : labelIds) {
                assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM tb_article_label WHERE labelid = ?",
                    Integer.class, labelId));
                assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM tb_label WHERE id = ?",
                    Integer.class, labelId));
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
    @Import({ArticleService.class, CommentService.class})
    static class Persistence {
        @Bean RedisTemplate redisTemplate() { return mock(RedisTemplate.class); }
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
