package com.liushao.user.auth;

import java.nio.file.Path;
import java.sql.Connection;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.ThreadLocalRandom;

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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.liushao.auth.JwtTokenService;
import com.liushao.user.dao.UserDao;
import com.liushao.user.pojo.ChangePasswordRequest;
import com.liushao.user.pojo.LoginRequest;
import com.liushao.user.pojo.RegisterRequest;
import com.liushao.user.service.AuthSessionService;
import com.liushao.user.service.AuthSessionService.Grant;
import com.liushao.user.service.UserService;
import com.liushao.util.IdWorker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 显式启用（HOW_AUTH_SERVICE_TEST_CONFIG，需绝对路径）后在真实 MySQL 上验收修改密码：
 * 注册随机手机号的临时用户、建立两个会话，验证改密后其他会话被撤销、当前会话保留、
 * 旧密码失效、新密码可登录；结束时按主键删除该用户的会话、摘要与用户行。
 */
@EnabledIfEnvironmentVariable(named = "HOW_AUTH_SERVICE_TEST_CONFIG", matches = ".+")
class UserPasswordMySqlTest {
    @Test
    void changesPasswordAndRevokesOtherSessionsOnRealMySql() {
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
            }
            JdbcTemplate jdbc = new JdbcTemplate(source);
            String mobile = "1" + String.format("%010d", ThreadLocalRandom.current().nextLong(10_000_000_000L));
            try (AnnotationConfigApplicationContext context = context(source)) {
                UserService service = context.getBean(UserService.class);
                AuthSessionService sessions = context.getBean(AuthSessionService.class);
                RegisterRequest register = new RegisterRequest();
                register.setMobile(mobile);
                register.setPassword("register-test-password");
                String userId = null;
                try {
                    stage = "register temp user";
                    assertEquals(UserService.RegisterResult.SUCCESS, service.register(register));
                    userId = jdbc.queryForObject("SELECT id FROM tb_user WHERE mobile = ?", String.class, mobile);
                    assertNotNull(userId);

                    stage = "create two sessions";
                    Grant sessionA = sessions.create(userId);
                    Grant sessionB = sessions.create(userId);

                    stage = "change password";
                    ChangePasswordRequest request = new ChangePasswordRequest();
                    request.setOldPassword("register-test-password");
                    request.setNewPassword("changed-password-789");
                    assertEquals(UserService.PasswordChangeResult.SUCCESS,
                            service.changePassword(userId, sessionA.getSessionId(), request));

                    stage = "row assertions";
                    String stored = jdbc.queryForObject(
                            "SELECT password FROM tb_user WHERE id = ?", String.class, userId);
                    assertTrue(stored.startsWith("$2"));
                    assertTrue(new BCryptPasswordEncoder().matches("changed-password-789", stored));
                    assertNull(jdbc.queryForObject("SELECT revoked_at FROM tb_auth_session WHERE id = ?",
                            java.sql.Timestamp.class, sessionA.getSessionId()));
                    assertNotNull(jdbc.queryForObject("SELECT revoked_at FROM tb_auth_session WHERE id = ?",
                            java.sql.Timestamp.class, sessionB.getSessionId()));

                    stage = "login only with new password";
                    LoginRequest oldLogin = new LoginRequest();
                    oldLogin.setMobile(mobile);
                    oldLogin.setPassword("register-test-password");
                    assertNull(service.login(oldLogin));
                    LoginRequest newLogin = new LoginRequest();
                    newLogin.setMobile(mobile);
                    newLogin.setPassword("changed-password-789");
                    assertNotNull(service.login(newLogin));
                } finally {
                    if (userId != null) {
                        jdbc.update("DELETE FROM tb_auth_refresh_token WHERE session_id IN "
                                + "(SELECT id FROM tb_auth_session WHERE user_id = ?)", userId);
                        jdbc.update("DELETE FROM tb_auth_session WHERE user_id = ?", userId);
                        jdbc.update("DELETE FROM tb_user WHERE id = ?", userId);
                    }
                }
            }
        } catch (Throwable failure) {
            int line = java.util.Arrays.stream(failure.getStackTrace())
                .filter(frame -> frame.getClassName().equals(getClass().getName()))
                .mapToInt(StackTraceElement::getLineNumber).findFirst().orElse(-1);
            fail("MySQL password verification failed at " + stage + " (details suppressed): "
                + failure.getClass().getSimpleName() + "; test line=" + line);
        } finally {
            logger.setLevel(previousLevel);
        }
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
    @EnableJpaRepositories(basePackageClasses = UserDao.class)
    @Import({UserService.class, AuthSessionService.class})
    static class Persistence {
        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder(4);
        }

        @Bean
        IdWorker idWorker() {
            return new IdWorker(1, 1);
        }

        @Bean
        JwtTokenService jwtTokenService() {
            return new JwtTokenService("01234567890123456789012345678901", Duration.ofMinutes(30));
        }

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
