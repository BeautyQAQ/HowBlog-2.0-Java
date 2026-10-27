package com.liushao.article.service;

import com.liushao.article.dao.CommentDao;
import com.liushao.article.dao.ArticleDao;
import com.liushao.article.dao.UserRoleDao;
import com.liushao.article.dao.CommentThumbupDao;
import com.liushao.article.pojo.Comment;
import com.liushao.article.pojo.Article;
import com.liushao.util.IdWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;
import java.util.List;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {
    @Mock
    private IdWorker idWorker;

    @Mock
    private CommentDao commentDao;

    @Mock
    private CommentThumbupDao commentThumbupDao;

    @Mock
    private RedisTemplate redisTemplate;

    @Mock
    private ValueOperations valueOperations;

    private CommentService commentService;

    @Mock private ArticleDao articleDao;
    @Mock private UserRoleDao userRoleDao;

    @BeforeEach
    void setUp() {
        commentService = new CommentService(idWorker, commentDao, commentThumbupDao, redisTemplate, articleDao, userRoleDao);
    }

    @Test
    void acceptsFirstThumbupForAUserAndIncrementsOnce() {
        stubValueOperations();
        stubLockedComment(commentOwnedBy("10002"));
        when(redisTemplate.hasKey("thumbup_10001_comment-1")).thenReturn(false);
        when(commentThumbupDao.insertIfAbsent("comment-1", "10001")).thenReturn(1);
        when(commentDao.incrementThumbup("comment-1")).thenReturn(1);

        CommentService.ThumbupResult result = commentService.thumbup("comment-1", "10001");

        assertEquals(CommentService.ThumbupResult.SUCCESS, result);
        verify(commentDao).incrementThumbup("comment-1");
        verify(redisTemplate, never()).delete("thumbup_10001_comment-1");
        verify(valueOperations).setIfAbsent("thumbup:v2:10001:comment-1", 1, Duration.ofHours(1));
    }

    @Test
    void rejectsDuplicateThumbupBeforeTouchingDatabase() {
        stubLockedComment(commentOwnedBy("10002"));
        when(commentThumbupDao.insertIfAbsent("comment-1", "10001")).thenReturn(0);

        CommentService.ThumbupResult result = commentService.thumbup("comment-1", "10001");

        assertEquals(CommentService.ThumbupResult.DUPLICATE, result);
        verify(commentDao, never()).incrementThumbup("comment-1");
    }

    @Test
    void removesRedisClaimWhenCommentDoesNotExist() {
        when(commentDao.findArticleId("comment-1")).thenReturn(Optional.empty());

        CommentService.ThumbupResult result = commentService.thumbup("comment-1", "10001");

        assertEquals(CommentService.ThumbupResult.NOT_FOUND, result);
        verify(commentThumbupDao, never()).insertIfAbsent("comment-1", "10001");
        verify(redisTemplate, never()).hasKey("thumbup_10001_comment-1");
    }

    @Test
    void removesRedisClaimWhenDatabaseUpdateFails() {
        RuntimeException failure = new RuntimeException("database unavailable");
        stubLockedComment(commentOwnedBy("10002"));
        when(redisTemplate.hasKey("thumbup_10001_comment-1")).thenReturn(false);
        when(commentThumbupDao.insertIfAbsent("comment-1", "10001")).thenReturn(1);
        when(commentDao.incrementThumbup("comment-1")).thenThrow(failure);

        assertThrows(
                RuntimeException.class,
                () -> commentService.thumbup("comment-1", "10001")
        );
        verify(redisTemplate, never()).delete("thumbup_10001_comment-1");
    }

    @Test
    void migratesLegacyRedisThumbupWithoutIncrementingAgain() {
        stubValueOperations();
        stubLockedComment(commentOwnedBy("10002"));
        when(redisTemplate.hasKey("thumbup_10001_comment-1")).thenReturn(true);
        when(commentThumbupDao.insertIfAbsent("comment-1", "10001")).thenReturn(1);

        CommentService.ThumbupResult result = commentService.thumbup("comment-1", "10001");

        assertEquals(CommentService.ThumbupResult.DUPLICATE, result);
        verify(commentDao, never()).incrementThumbup("comment-1");
        verify(valueOperations).setIfAbsent("thumbup:v2:10001:comment-1", 1, Duration.ofHours(1));
    }

    @Test
    void fallsBackToDatabaseWhenRedisIsUnavailable() {
        when(redisTemplate.opsForValue()).thenThrow(new IllegalStateException("redis unavailable"));
        stubLockedComment(commentOwnedBy("10002"));
        when(redisTemplate.hasKey("thumbup_10001_comment-1")).thenThrow(new IllegalStateException("redis unavailable"));
        when(commentThumbupDao.insertIfAbsent("comment-1", "10001")).thenReturn(1);
        when(commentDao.incrementThumbup("comment-1")).thenReturn(1);

        CommentService.ThumbupResult result = commentService.thumbup("comment-1", "10001");

        assertEquals(CommentService.ThumbupResult.SUCCESS, result);
        verify(commentDao).incrementThumbup("comment-1");
    }

    @Test
    void preservesDatabaseFailureWhenRedisReleaseAlsoFails() {
        RuntimeException databaseFailure = new RuntimeException("database unavailable");
        stubLockedComment(commentOwnedBy("10002"));
        when(redisTemplate.hasKey("thumbup_10001_comment-1")).thenReturn(false);
        when(commentThumbupDao.insertIfAbsent("comment-1", "10001")).thenReturn(1);
        when(commentDao.incrementThumbup("comment-1")).thenThrow(databaseFailure);

        RuntimeException failure = assertThrows(
                RuntimeException.class,
                () -> commentService.thumbup("comment-1", "10001")
        );

        assertEquals(databaseFailure, failure);
    }

    @Test
    void preventsNonAuthorFromUpdatingOrDeletingComment() {
        Comment comment = commentOwnedBy("10002");
        stubLockedComment(comment);

        Comment update = new Comment();
        update.set_id("comment-1");
        update.setContent("forged edit");

        assertFalse(commentService.update(update, "10001"));
        assertFalse(commentService.deleteById("comment-1", "10001"));
        verify(commentDao, never()).save(comment);
        verify(commentDao, never()).delete(comment);
    }

    @Test
    void keepsCommentPlacementFieldsWhenAuthorEditsContent() {
        Comment existing = commentOwnedBy("10001");
        existing.setArticleid("article-1");
        existing.setParentid("parent-1");
        stubLockedComment(existing);

        Comment update = new Comment();
        update.set_id("comment-1");
        update.setArticleid("article-2");
        update.setParentid("parent-2");
        update.setContent("edited content");

        assertEquals(true, commentService.update(update, "10001"));
        assertEquals("article-1", existing.getArticleid());
        assertEquals("parent-1", existing.getParentid());
        assertEquals("edited content", existing.getContent());
        verify(commentDao).save(existing);
    }

    private Comment commentOwnedBy(String userId) {
        Comment comment = new Comment();
        comment.set_id("comment-1");
        comment.setUserid(userId);
        comment.setArticleid("article-1");
        return comment;
    }

    @Test
    void deletesBranchWhenHistoricalZeroCounterReportsNoChangedRows() {
        Comment root = commentOwnedBy("10001");
        stubLockedComment(root);
        when(commentDao.findByArticleIdForUpdate("article-1")).thenReturn(List.of(root));
        when(commentDao.deleteComments(List.of("comment-1"))).thenReturn(1);
        when(articleDao.decrementComment("article-1", 1)).thenReturn(0);

        assertEquals(true, commentService.deleteById("comment-1", "10001"));
        verify(commentThumbupDao).deleteByCommentIds(List.of("comment-1"));
        verify(articleDao).decrementComment("article-1", 1);
    }

    private void stubLockedComment(Comment comment) {
        Article article = new Article();
        article.setId(comment.getArticleid());
        article.setState(Article.STATE_PUBLISHED);
        when(commentDao.findArticleId("comment-1")).thenReturn(Optional.of(comment.getArticleid()));
        when(articleDao.findByIdForUpdate(comment.getArticleid())).thenReturn(Optional.of(article));
        when(commentDao.findByIdForUpdate("comment-1")).thenReturn(Optional.of(comment));
    }

    private void stubValueOperations() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }
}
