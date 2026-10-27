package com.liushao.user.service;

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

import com.liushao.entity.PageResult;
import com.liushao.user.dao.UserDao;
import com.liushao.user.pojo.UserSummaryResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 由 HOW_AUTH_SERVICE_TEST_CONFIG 显式启用的真实 MySQL 验收：验证关注唯一关系、粉丝/关注计数与
 * 粉丝/关注列表，覆盖首次、重复、自己、缺失、取关、并发。测试数据按主键精确清理，默认跳过。
 */
@EnabledIfEnvironmentVariable(named = "HOW_AUTH_SERVICE_TEST_CONFIG", matches = ".+")
class UserFollowMySqlTest {

    @Test
    void verifiesFollowConsistencyAgainstRealMySql() {
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger)
                org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ch.qos.logback.classic.Level previousLevel = logger.getLevel();
        logger.setLevel(ch.qos.logback.classic.Level.OFF);
        String stage = "configuration";
        List<String> userIds = new ArrayList<>();
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
            stage = "follow table precondition";
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables "
                    + "WHERE table_schema = DATABASE() AND table_name = 'tb_user_follow'", Integer.class));
            try (AnnotationConfigApplicationContext context = context(source)) {
                UserFollowService service = context.getBean(UserFollowService.class);

                stage = "follow and duplicate";
                String alice = insertUser(jdbc, userIds);
                String bob = insertUser(jdbc, userIds);
                assertEquals(false, service.isFollowing(alice, bob));
                assertEquals(UserFollowService.FollowResult.SUCCESS, service.follow(alice, bob));
                assertEquals(true, service.isFollowing(alice, bob));
                assertEquals(false, service.isFollowing(bob, alice));
                assertEquals(1, relationCount(jdbc, alice, bob));
                assertEquals(1, fansCount(jdbc, bob));
                assertEquals(1, followCount(jdbc, alice));
                assertEquals(UserFollowService.FollowResult.DUPLICATE, service.follow(alice, bob));
                assertEquals(1, relationCount(jdbc, alice, bob));
                assertEquals(1, fansCount(jdbc, bob));

                stage = "self and missing target";
                assertEquals(UserFollowService.FollowResult.SELF, service.follow(alice, alice));
                assertEquals(UserFollowService.FollowResult.NOT_FOUND,
                        service.follow(alice, "follow-test-missing-" + UUID.randomUUID()));

                stage = "fans and follows lists";
                PageResult<UserSummaryResponse> fans = service.fans(bob, 1, 10);
                assertEquals(1L, fans.getTotal());
                assertEquals(alice, fans.getRows().get(0).getId());
                PageResult<UserSummaryResponse> follows = service.follows(alice, 1, 10);
                assertEquals(1L, follows.getTotal());
                assertEquals(bob, follows.getRows().get(0).getId());

                stage = "unfollow";
                assertEquals(UserFollowService.FollowResult.SUCCESS, service.unfollow(alice, bob));
                assertEquals(0, relationCount(jdbc, alice, bob));
                assertEquals(0, fansCount(jdbc, bob));
                assertEquals(0, followCount(jdbc, alice));
                assertEquals(UserFollowService.FollowResult.NOT_FOLLOWING, service.unfollow(alice, bob));

                stage = "concurrent follow of the same pair";
                String carol = insertUser(jdbc, userIds);
                String dave = insertUser(jdbc, userIds);
                List<UserFollowService.FollowResult> results = race(service, carol, dave);
                assertEquals(1, results.stream().filter(r -> r == UserFollowService.FollowResult.SUCCESS).count());
                assertEquals(19, results.stream().filter(r -> r == UserFollowService.FollowResult.DUPLICATE).count());
                assertEquals(1, relationCount(jdbc, carol, dave));
                assertEquals(1, fansCount(jdbc, dave));
                assertEquals(1, followCount(jdbc, carol));
            }
            stage = "cleanup verification";
            cleanup(jdbc, userIds);
            assertNoLeftovers(jdbc, userIds);
        } catch (Throwable failure) {
            int line = java.util.Arrays.stream(failure.getStackTrace())
                    .filter(frame -> frame.getClassName().equals(getClass().getName()))
                    .mapToInt(StackTraceElement::getLineNumber).findFirst().orElse(-1);
            fail("User follow storage verification failed at " + stage + " (details suppressed): "
                    + failure.getClass().getSimpleName() + "; test line=" + line);
        } finally {
            if (jdbc != null) {
                try {
                    cleanup(jdbc, userIds);
                } catch (RuntimeException ignored) {
                    // 失败路径的兜底清理，清理结果由主流程断言负责
                }
            }
            logger.setLevel(previousLevel);
        }
    }

    private List<UserFollowService.FollowResult> race(UserFollowService service, String followerId, String followeeId)
            throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(20);
        try {
            List<Future<UserFollowService.FollowResult>> futures = new ArrayList<>();
            for (int index = 0; index < 20; index++) {
                futures.add(executor.submit(() -> {
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    return service.follow(followerId, followeeId);
                }));
            }
            start.countDown();
            List<UserFollowService.FollowResult> results = new ArrayList<>();
            for (Future<UserFollowService.FollowResult> future : futures) {
                try {
                    results.add(future.get(60, TimeUnit.SECONDS));
                } catch (java.util.concurrent.ExecutionException wrapped) {
                    Throwable cause = wrapped.getCause();
                    if (cause instanceof RuntimeException runtime) {
                        throw runtime;
                    }
                    throw new IllegalStateException(cause);
                }
            }
            return results;
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
        }
    }

    private String insertUser(JdbcTemplate jdbc, List<String> userIds) {
        String id = "follow-test-" + UUID.randomUUID();
        String mobile = "ft" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        jdbc.update("INSERT INTO tb_user (id, mobile, password, nickname, regdate, online, fanscount, followcount) "
                        + "VALUES (?, ?, ?, ?, UTC_TIMESTAMP(), 0, 0, 0)",
                id, mobile, "$2a$10$followtest", "follow-test-user");
        userIds.add(id);
        return id;
    }

    private int relationCount(JdbcTemplate jdbc, String followerId, String followeeId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM tb_user_follow WHERE follower_id = ? AND followee_id = ?",
                Integer.class, followerId, followeeId);
    }

    private int fansCount(JdbcTemplate jdbc, String userId) {
        return jdbc.queryForObject("SELECT fanscount FROM tb_user WHERE id = ?", Integer.class, userId);
    }

    private int followCount(JdbcTemplate jdbc, String userId) {
        return jdbc.queryForObject("SELECT followcount FROM tb_user WHERE id = ?", Integer.class, userId);
    }

    private void cleanup(JdbcTemplate jdbc, List<String> userIds) {
        for (String userId : userIds) {
            jdbc.update("DELETE FROM tb_user_follow WHERE follower_id = ? OR followee_id = ?", userId, userId);
            jdbc.update("DELETE FROM tb_user WHERE id = ?", userId);
        }
    }

    private void assertNoLeftovers(JdbcTemplate jdbc, List<String> userIds) {
        for (String userId : userIds) {
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM tb_user_follow "
                    + "WHERE follower_id = ? OR followee_id = ?", Integer.class, userId, userId));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM tb_user WHERE id = ?",
                    Integer.class, userId));
        }
    }

    private Properties readSettings() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new FileSystemResource(Path.of(System.getenv("HOW_AUTH_SERVICE_TEST_CONFIG")).toAbsolutePath()));
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
    @EnableJpaRepositories(basePackageClasses = UserDao.class)
    @Import(UserFollowService.class)
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
