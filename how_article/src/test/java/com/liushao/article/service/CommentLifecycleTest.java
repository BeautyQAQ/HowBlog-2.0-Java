package com.liushao.article.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.liushao.article.dao.CommentDao;
import com.liushao.article.pojo.Comment;
import com.liushao.util.IdWorker;
import com.liushao.web.InvalidRequestException;
import com.liushao.web.ResourceNotFoundException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class CommentLifecycleTest {
    private AnnotationConfigApplicationContext context;
    private CommentService comments;
    private ArticleService articles;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext();
        context.registerBean(DataSource.class, () -> new DriverManagerDataSource(
                "jdbc:h2:mem:comments-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000", "sa", ""));
        context.register(Persistence.class);
        context.refresh();
        comments = context.getBean(CommentService.class);
        articles = context.getBean(ArticleService.class);
        jdbc = new JdbcTemplate(context.getBean(DataSource.class));
        jdbc.execute("CREATE TABLE IF NOT EXISTS tb_user (id VARCHAR(64) PRIMARY KEY)");
        article("published", "author", "1", 0);
    }

    @AfterEach
    void tearDown() {
        if (context != null) context.close();
    }

    @Test
    void createsRootsAndRepliesAndAtomicallyCountsThemWithoutChangingOtherCounters() {
        Comment root = draft("published", " \t ");
        root.setUserid("forged");
        root.setThumbup(999);
        comments.save(root, "reader");
        Comment reply = draft("published", root.get_id());
        comments.save(reply, "another-reader");
        assertNull(root.getParentid());
        assertEquals("reader", root.getUserid());
        assertEquals(0, root.getThumbup());
        assertNotNull(root.getPublishdate());
        assertEquals(2, count("published"));
        assertEquals(7, jdbc.queryForObject("SELECT visits FROM tb_article WHERE id='published'", Integer.class));
        assertEquals(9, jdbc.queryForObject("SELECT thumbup FROM tb_article WHERE id='published'", Integer.class));
        assertEquals(root.get_id(), comments.findById(reply.get_id(), null).getParentid());
    }

    @Test
    void rejectsMissingPrivateArticlesAndMissingOrCrossArticleParentsWithoutPartialWrites() {
        article("draft", "author", "0", 0);
        article("pending", "author", "2", 0);
        for (String id : List.of("missing", "draft", "pending")) {
            assertThrows(ResourceNotFoundException.class, () -> comments.save(draft(id, null), "author"));
        }
        assertThrows(InvalidRequestException.class,
                () -> comments.save(draft("published", "missing"), "reader"));
        article("other", "author", "1", 0);
        Comment otherRoot = draft("other", null);
        comments.save(otherRoot, "reader");
        assertThrows(InvalidRequestException.class,
                () -> comments.save(draft("published", otherRoot.get_id()), "reader"));
        Comment blank = draft("published", null);
        blank.setContent(" \t ");
        assertThrows(InvalidRequestException.class, () -> comments.save(blank, "reader"));
        assertEquals(0, count("published"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM tb_comment", Integer.class));
    }

    @Test
    void deletesOnlyTheAuthorsBranchWithAllDescendantsAndThumbupRelations() {
        Comment root = save("published", null, "reader");
        Comment reply = save("published", root.get_id(), "another");
        Comment grandchild = save("published", reply.get_id(), "third");
        Comment unrelated = save("published", null, "another");
        for (Comment c : List.of(root, reply, grandchild, unrelated)) {
            jdbc.update("INSERT INTO tb_comment_thumbup (commentid, userid) VALUES (?, ?)", c.get_id(), "liker");
        }
        assertFalse(comments.deleteById(root.get_id(), "another"));
        assertEquals(4, count("published"));
        assertTrue(comments.deleteById(root.get_id(), "reader"));
        assertEquals(1, count("published"));
        assertEquals(List.of(unrelated.get_id()), jdbc.queryForList("SELECT id FROM tb_comment", String.class));
        assertEquals(List.of(unrelated.get_id()), jdbc.queryForList("SELECT commentid FROM tb_comment_thumbup", String.class));
        assertFalse(comments.deleteById(root.get_id(), "reader"));
    }

    @Test
    void articleDeletionRemovesCommentsAndLikesWithoutTouchingAnotherArticle() {
        Comment root = save("published", null, "reader");
        save("published", root.get_id(), "another");
        jdbc.update("INSERT INTO tb_comment_thumbup (commentid, userid) VALUES (?, ?)", root.get_id(), "liker");
        article("other", "author", "1", 0);
        Comment other = save("other", null, "reader");
        assertFalse(articles.delete("published", "reader"));
        assertTrue(articles.delete("published", "author"));
        assertEquals(List.of(other.get_id()), jdbc.queryForList("SELECT id FROM tb_comment", String.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM tb_comment_thumbup", Integer.class));
        assertEquals(1, count("other"));
    }

    @Test
    void privateArticleCommentsFollowArticleVisibilityAndOrphansAreHidden() {
        Comment visible = save("published", null, "reader");
        article("private", "author", "0", 1);
        rawComment("private-comment", "private");
        rawComment("orphan", "missing");
        jdbc.update("INSERT INTO tb_user (id) VALUES ('admin')");
        jdbc.update("INSERT INTO tb_user_role (user_id, role) VALUES ('admin', 'ADMIN')");
        assertEquals(List.of(visible.get_id()), comments.findAll(null).stream().map(Comment::get_id).toList());
        assertEquals(2, comments.findAll("author").size());
        assertEquals(2, comments.findAll("admin").size());
        assertThrows(ResourceNotFoundException.class, () -> comments.findById("private-comment", null));
        assertThrows(ResourceNotFoundException.class, () -> comments.findByarticleId("private", "reader"));
        assertEquals("private-comment", comments.findById("private-comment", "author").get_id());
        assertEquals(1, comments.findByarticleId("private", "admin").size());
        assertThrows(ResourceNotFoundException.class, () -> comments.findById("orphan", "admin"));
        assertThrows(ResourceNotFoundException.class, () -> comments.findByarticleId("missing", null));
        assertEquals(CommentService.ThumbupResult.NOT_FOUND, comments.thumbup("private-comment", "author"));
    }

    @Test
    void contentEditPreservesPlacementAndCommittedThumbupCountAndAuthorsCanDeleteAfterUnpublish() {
        Comment root = save("published", null, "reader");
        jdbc.update("UPDATE tb_comment SET thumbup=5 WHERE id=?", root.get_id());
        Comment edit = draft("forged-article", "forged-parent");
        edit.set_id(root.get_id());
        edit.setContent("edited");
        assertFalse(comments.update(edit, "other"));
        assertTrue(comments.update(edit, "reader"));
        Comment stored = comments.findById(root.get_id(), null);
        assertEquals("published", stored.getArticleid());
        assertNull(stored.getParentid());
        assertEquals("edited", stored.getContent());
        assertEquals(5, stored.getThumbup());
        jdbc.update("UPDATE tb_article SET state='0' WHERE id='published'");
        assertTrue(comments.deleteById(root.get_id(), "reader"));
        assertEquals(0, count("published"));
    }

    @Test
    void simultaneousCommentCreatesDoNotLoseCounts() throws Exception {
        int writers = 12;
        var executor = Executors.newFixedThreadPool(writers);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < writers; i++) {
                futures.add(executor.submit(() -> {
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    comments.save(draft("published", null), "reader");
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) future.get(15, TimeUnit.SECONDS);
            assertEquals(writers, count("published"));
            assertEquals(writers, jdbc.queryForObject("SELECT COUNT(*) FROM tb_comment", Integer.class));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void failedCountIncrementRollsBackCommentCreation() {
        jdbc.execute("ALTER TABLE tb_article ADD CONSTRAINT test_no_increment CHECK (comment <= 0)");
        assertThrows(RuntimeException.class, () -> comments.save(draft("published", null), "reader"));
        assertEquals(0, count("published"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM tb_comment", Integer.class));
    }

    @Test
    void failedCountDecrementRollsBackBranchAndThumbupDeletion() {
        Comment root = save("published", null, "reader");
        jdbc.update("INSERT INTO tb_comment_thumbup (commentid, userid) VALUES (?, 'liker')", root.get_id());
        jdbc.execute("ALTER TABLE tb_article ADD CONSTRAINT test_no_decrement CHECK (comment >= 1)");
        assertThrows(RuntimeException.class, () -> comments.deleteById(root.get_id(), "reader"));
        assertEquals(1, count("published"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM tb_comment", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM tb_comment_thumbup", Integer.class));
    }

    @Test
    void replyRacingBranchDeletionCannotLeaveAnOrphanOrWrongCount() throws Exception {
        Comment root = save("published", null, "reader");
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> create = executor.submit(() -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                try {
                    comments.save(draft("published", root.get_id()), "another");
                } catch (InvalidRequestException expected) {
                    // Deletion may commit first; the missing parent must then reject this reply.
                }
                return null;
            });
            Future<?> delete = executor.submit(() -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                assertTrue(comments.deleteById(root.get_id(), "reader"));
                return null;
            });
            start.countDown();
            create.get(15, TimeUnit.SECONDS);
            delete.get(15, TimeUnit.SECONDS);
            assertEquals(0, count("published"));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM tb_comment", Integer.class));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private Comment save(String articleId, String parentId, String userId) {
        Comment comment = draft(articleId, parentId);
        comments.save(comment, userId);
        return comment;
    }

    private Comment draft(String articleId, String parentId) {
        Comment comment = new Comment();
        comment.setArticleid(articleId);
        comment.setParentid(parentId);
        comment.setContent("test comment");
        return comment;
    }

    private int count(String articleId) {
        return jdbc.queryForObject("SELECT comment FROM tb_article WHERE id=?", Integer.class, articleId);
    }

    private void article(String id, String author, String state, int count) {
        jdbc.update("INSERT INTO tb_article (id, userid, title, content, state, comment, visits, thumbup) "
                + "VALUES (?, ?, 'title', 'body', ?, ?, 7, 9)", id, author, state, count);
    }

    private void rawComment(String id, String articleId) {
        jdbc.update("INSERT INTO tb_comment (id, articleid, content, userid, thumbup) VALUES (?, ?, 'body', 'reader', 0)",
                id, articleId);
    }

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = CommentDao.class)
    @Import({CommentService.class, ArticleService.class})
    static class Persistence {
        @Bean IdWorker idWorker() { return new IdWorker(2, 2); }
        @Bean RedisTemplate redisTemplate() { return mock(RedisTemplate.class); }
        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(source);
            factory.setPackagesToScan("com.liushao.article.pojo");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            Properties options = new Properties();
            options.setProperty("hibernate.hbm2ddl.auto", "create-drop");
            options.setProperty("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
            options.setProperty("hibernate.show_sql", "false");
            factory.setJpaProperties(options);
            return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }
    }
}
