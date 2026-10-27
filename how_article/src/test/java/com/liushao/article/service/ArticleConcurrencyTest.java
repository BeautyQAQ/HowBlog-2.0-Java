package com.liushao.article.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;

import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import com.liushao.article.dao.ArticleDao;
import com.liushao.article.dao.ArticleLabelDao;
import com.liushao.article.dao.ArticleThumbupDao;
import com.liushao.article.dao.CommentDao;
import com.liushao.article.dao.CommentThumbupDao;
import com.liushao.article.dao.UserRoleDao;
import com.liushao.article.pojo.Article;
import com.liushao.util.IdWorker;
import com.liushao.web.InvalidRequestException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArticleConcurrencyTest {
    private static AnnotationConfigApplicationContext context;
    private static JdbcTemplate jdbc;
    private static ArticleService service;
    private static ArticleDao articles;
    private static TransactionTemplate transaction;
    private static ReadGate gate;

    @BeforeAll
    static void startIsolatedDatabase() {
        context = new AnnotationConfigApplicationContext(Persistence.class);
        jdbc = new JdbcTemplate(context.getBean(DataSource.class));
        service = context.getBean(ArticleService.class);
        articles = context.getBean(ArticleDao.class);
        transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        gate = context.getBean(ReadGate.class);
        jdbc.execute("CREATE TABLE tb_user (id VARCHAR(64) PRIMARY KEY)");
        jdbc.update("INSERT INTO tb_user VALUES ('admin')");
        jdbc.update("INSERT INTO tb_user_role (user_id, role) VALUES ('admin', 'ADMIN')");
        jdbc.execute("CREATE TABLE article_delete_guard (articleid VARCHAR(255) PRIMARY KEY "
                + "REFERENCES tb_article(id))");
        jdbc.execute("CREATE TABLE tb_label (id VARCHAR(255) PRIMARY KEY, count BIGINT)");
    }

    @AfterAll
    static void stopIsolatedDatabase() {
        if (context != null) context.close();
    }

    @BeforeEach
    void resetArticle() {
        gate.reset();
        jdbc.update("DELETE FROM article_delete_guard");
        jdbc.update("DELETE FROM tb_comment_thumbup");
        jdbc.update("DELETE FROM tb_comment");
        jdbc.update("DELETE FROM tb_article_label");
        jdbc.update("DELETE FROM tb_label");
        jdbc.update("DELETE FROM tb_article");
        Article article = new Article();
        article.setId("article");
        article.setUserid("author");
        article.setTitle("original");
        article.setContent("body");
        article.setState(Article.STATE_DRAFT);
        article.setVisits(3);
        article.setThumbup(5);
        article.setComment(7);
        articles.saveAndFlush(article);
    }

    @ParameterizedTest
    @EnumSource(Mutation.class)
    void contentAndStateWritesPreserveConcurrentCounters(Mutation mutation) throws Exception {
        jdbc.update("UPDATE tb_article SET state = ? WHERE id = 'article'", mutation.initialState);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> write = executor.submit(() -> {
                gate.writer = Thread.currentThread();
                mutation.apply(service);
            });
            assertTrue(gate.loaded.await(10, TimeUnit.SECONDS));
            Future<?> counters = executor.submit(() -> transaction.execute(status -> {
                articles.incrementVisits("article");
                articles.incrementThumbup("article");
                articles.incrementComment("article");
                return null;
            }));
            awaitCommittedOrBlocked(counters);
            gate.resume.countDown();
            write.get(10, TimeUnit.SECONDS);
            counters.get(10, TimeUnit.SECONDS);

            assertEquals(List.of(4, 6, 8), jdbc.queryForObject(
                    "SELECT visits, thumbup, comment FROM tb_article WHERE id = 'article'",
                    (row, number) -> List.of(row.getInt(1), row.getInt(2), row.getInt(3))));
            assertEquals(mutation.finalState, jdbc.queryForObject(
                    "SELECT state FROM tb_article WHERE id = 'article'", String.class));
        } finally {
            gate.resume.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void competingReviewsRecheckStateAfterObtainingLock() throws Exception {
        jdbc.update("UPDATE tb_article SET state = ? WHERE id = 'article'", Article.STATE_PENDING);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ArticleService.TransitionResult> approved = executor.submit(() -> {
                gate.writer = Thread.currentThread();
                return service.approve("article", "admin");
            });
            assertTrue(gate.loaded.await(10, TimeUnit.SECONDS));
            Future<ArticleService.TransitionResult> rejected = executor.submit(
                    () -> service.reject("article", "admin"));
            awaitCommittedOrBlocked(rejected);
            gate.resume.countDown();

            assertEquals(ArticleService.TransitionResult.SUCCESS, approved.get(10, TimeUnit.SECONDS));
            assertEquals(ArticleService.TransitionResult.INVALID_STATE, rejected.get(10, TimeUnit.SECONDS));
            assertEquals(Article.STATE_PUBLISHED, jdbc.queryForObject(
                    "SELECT state FROM tb_article WHERE id = 'article'", String.class));
        } finally {
            gate.resume.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void parallelReadsReturnTheirOwnCommittedVisitCounts() throws Exception {
        jdbc.update("UPDATE tb_article SET state = ? WHERE id = 'article'", Article.STATE_PUBLISHED);
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int index = 0; index < 8; index++) {
                results.add(executor.submit(() -> service.findById("article", null).getVisits()));
            }
            HashSet<Integer> visits = new HashSet<>();
            for (Future<Integer> result : results) visits.add(result.get(10, TimeUnit.SECONDS));
            assertEquals(new HashSet<>(List.of(4, 5, 6, 7, 8, 9, 10, 11)), visits);
            assertEquals(11, jdbc.queryForObject("SELECT visits FROM tb_article WHERE id = 'article'", Integer.class));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void articleDeletionRemovesItsCommentsAndLikesOnly() {
        insertCommentFixtures();

        assertFalse(service.delete("article", "another-user"));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM tb_comment", Integer.class));
        assertTrue(service.delete("article", "author"));

        assertFalse(articles.existsById("article"));
        assertEquals(List.of("other-comment"), jdbc.queryForList("SELECT id FROM tb_comment", String.class));
        assertEquals(List.of("other-comment"), jdbc.queryForList(
                "SELECT commentid FROM tb_comment_thumbup", String.class));
    }

    @Test
    void failedArticleDeletionRollsBackCommentAndLikeCleanup() {
        insertCommentFixtures();
        jdbc.update("INSERT INTO article_delete_guard VALUES ('article')");

        assertThrows(RuntimeException.class, () -> service.delete("article", "author"));

        assertTrue(articles.existsById("article"));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM tb_comment", Integer.class));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM tb_comment_thumbup", Integer.class));
    }

    @Test
    void labelDeletedAfterValidationRollsBackArticleAndPartialRelations() {
        jdbc.update("INSERT INTO tb_label (id, count) VALUES ('remaining', 5), ('removed', 7)");
        TransactionTemplate concurrentDelete = new TransactionTemplate(
                context.getBean(PlatformTransactionManager.class));
        concurrentDelete.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        gate.beforeLabelInsert = () -> concurrentDelete.executeWithoutResult(status ->
                jdbc.update("DELETE FROM tb_label WHERE id = 'removed'"));
        Article article = new Article();
        article.setTitle("new article");
        article.setContent("body");
        article.setLabelIds(List.of("remaining", "removed"));

        assertThrows(InvalidRequestException.class, () -> service.add(article, "author"));

        assertFalse(articles.existsById(article.getId()));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM tb_article_label", Integer.class));
        assertEquals(5L, jdbc.queryForObject("SELECT count FROM tb_label WHERE id = 'remaining'", Long.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM tb_label WHERE id = 'removed'", Integer.class));
    }

    private void insertCommentFixtures() {
        jdbc.update("INSERT INTO tb_comment (id, articleid, content, parentid) VALUES "
                + "('root', 'article', 'comment', NULL), ('reply', 'article', 'reply', 'root'), "
                + "('other-comment', 'other-article', 'another article comment', NULL)");
        jdbc.update("INSERT INTO tb_comment_thumbup (commentid, userid) VALUES "
                + "('root', 'viewer'), ('reply', 'viewer'), ('other-comment', 'viewer')");
    }

    private void awaitCommittedOrBlocked(Future<?> concurrentWrite) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!concurrentWrite.isDone()) {
            if (jdbc.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.SESSIONS "
                    + "WHERE BLOCKER_ID IS NOT NULL", Integer.class) > 0) return;
            assertTrue(System.nanoTime() < deadline, "Concurrent write neither completed nor waited for a row lock");
            Thread.sleep(10);
        }
        concurrentWrite.get(10, TimeUnit.SECONDS);
    }

    enum Mutation {
        EDIT(Article.STATE_PUBLISHED, Article.STATE_PUBLISHED),
        PUBLISH(Article.STATE_DRAFT, Article.STATE_PUBLISHED),
        UNPUBLISH(Article.STATE_PUBLISHED, Article.STATE_DRAFT),
        SUBMIT(Article.STATE_DRAFT, Article.STATE_PENDING),
        APPROVE(Article.STATE_PENDING, Article.STATE_PUBLISHED),
        REJECT(Article.STATE_PENDING, Article.STATE_DRAFT);

        private final String initialState;
        private final String finalState;

        Mutation(String initialState, String finalState) {
            this.initialState = initialState;
            this.finalState = finalState;
        }

        void apply(ArticleService service) {
            switch (this) {
                case EDIT -> {
                    Article update = new Article();
                    update.setId("article");
                    update.setTitle("edited");
                    assertTrue(service.update(update, "author"));
                }
                case PUBLISH -> assertTrue(service.publish("article", "author"));
                case UNPUBLISH -> assertTrue(service.unpublish("article", "author"));
                case SUBMIT -> assertEquals(ArticleService.TransitionResult.SUCCESS,
                        service.submit("article", "author"));
                case APPROVE -> assertEquals(ArticleService.TransitionResult.SUCCESS,
                        service.approve("article", "admin"));
                case REJECT -> assertEquals(ArticleService.TransitionResult.SUCCESS,
                        service.reject("article", "admin"));
            }
        }
    }

    static class ReadGate {
        volatile Thread writer;
        Runnable beforeLabelInsert;
        CountDownLatch loaded;
        CountDownLatch resume;
        AtomicBoolean pending;

        void reset() {
            writer = null;
            beforeLabelInsert = null;
            loaded = new CountDownLatch(1);
            resume = new CountDownLatch(1);
            pending = new AtomicBoolean(true);
        }
    }

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = ArticleDao.class)
    static class Persistence {
        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:article-concurrency-" + UUID.randomUUID()
                    + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000", "sa", "");
        }

        @Bean
        ReadGate readGate() {
            return new ReadGate();
        }

        @Bean
        ArticleService articleService(ArticleDao articles, ArticleLabelDao labels, ArticleThumbupDao thumbups,
                                      UserRoleDao roles, CommentDao comments, CommentThumbupDao commentThumbups, ReadGate gate) {
            ProxyFactory proxy = new ProxyFactory(articles);
            proxy.addAdvice((MethodInterceptor) invocation -> {
                Object result = invocation.proceed();
                if (Thread.currentThread() == gate.writer
                        && (invocation.getMethod().getName().equals("findById")
                        || invocation.getMethod().getName().equals("findByIdForUpdate"))
                        && gate.pending.compareAndSet(true, false)) {
                    gate.loaded.countDown();
                    assertTrue(gate.resume.await(10, TimeUnit.SECONDS));
                }
                return result;
            });
            ProxyFactory labelProxy = new ProxyFactory(labels);
            labelProxy.addAdvice((MethodInterceptor) invocation -> {
                if (invocation.getMethod().getName().equals("insertExistingLabels")) {
                    Runnable hook = gate.beforeLabelInsert;
                    gate.beforeLabelInsert = null;
                    if (hook != null) hook.run();
                }
                return invocation.proceed();
            });
            return new ArticleService((ArticleDao) proxy.getProxy(), new IdWorker(1, 1), roles, thumbups,
                    (ArticleLabelDao) labelProxy.getProxy(), comments, commentThumbups);
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
            LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(source);
            factory.setPackagesToScan("com.liushao.article.pojo");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            Properties properties = new Properties();
            properties.setProperty("hibernate.hbm2ddl.auto", "create-drop");
            properties.setProperty("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
            factory.setJpaProperties(properties);
            return factory;
        }

        @Bean
        PlatformTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }
    }
}
