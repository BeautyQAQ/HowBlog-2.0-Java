package com.liushao.article.service;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import com.liushao.article.dao.ArticleDao;
import com.liushao.article.dao.CommentDao;
import com.liushao.article.dao.CommentThumbupDao;
import com.liushao.article.dao.UserRoleDao;
import com.liushao.article.pojo.Article;
import com.liushao.article.pojo.Comment;
import com.liushao.util.IdWorker;
import com.liushao.web.InvalidRequestException;
import com.liushao.web.ResourceNotFoundException;

@Service
public class CommentService {
    private static final Duration REDIS_CLAIM_TTL = Duration.ofHours(1);
    private static final String LEGACY_THUMBUP_PREFIX = "thumbup_";
    private static final String THUMBUP_CACHE_PREFIX = "thumbup:v2:";
    private static final int DELETE_BATCH_SIZE = 500;

    private final IdWorker idWorker;
    private final CommentDao commentDao;
    private final CommentThumbupDao commentThumbupDao;
    private final RedisTemplate redisTemplate;
    private final ArticleDao articleDao;
    private final UserRoleDao userRoleDao;

    public CommentService(IdWorker idWorker, CommentDao commentDao, CommentThumbupDao commentThumbupDao,
                          RedisTemplate redisTemplate, ArticleDao articleDao, UserRoleDao userRoleDao) {
        this.idWorker = idWorker;
        this.commentDao = commentDao;
        this.commentThumbupDao = commentThumbupDao;
        this.redisTemplate = redisTemplate;
        this.articleDao = articleDao;
        this.userRoleDao = userRoleDao;
    }

    public Comment findById(String id, String viewerId) {
        Comment comment = commentDao.findById(id).orElseThrow(ResourceNotFoundException::new);
        requireVisibleArticle(comment.getArticleid(), viewerId);
        return comment;
    }

    public List<Comment> findAll(String viewerId) {
        return commentDao.findVisible(viewerId, isAdministrator(viewerId));
    }

    @Transactional
    public void save(Comment comment, String userId) {
        if (comment == null || !StringUtils.hasText(comment.getArticleid())
                || !StringUtils.hasText(comment.getContent())) {
            throw new InvalidRequestException();
        }
        Article article = articleDao.findByIdForUpdate(comment.getArticleid())
                .filter(existing -> Article.STATE_PUBLISHED.equals(existing.getState()))
                .orElseThrow(ResourceNotFoundException::new);
        String parentId = StringUtils.hasText(comment.getParentid()) ? comment.getParentid().strip() : null;
        if (parentId != null) {
            Comment parent = commentDao.findByIdForUpdate(parentId).orElseThrow(InvalidRequestException::new);
            if (!Objects.equals(article.getId(), parent.getArticleid())) {
                throw new InvalidRequestException();
            }
        }
        comment.set_id(String.valueOf(idWorker.nextId()));
        comment.setUserid(userId);
        comment.setParentid(parentId);
        comment.setPublishdate(new Date());
        comment.setThumbup(0);
        commentDao.saveAndFlush(comment);
        if (articleDao.incrementComment(article.getId()) != 1) {
            throw new IllegalStateException("Comment count update failed");
        }
    }

    @Transactional
    public boolean update(Comment comment, String userId) {
        if (comment == null || (comment.getContent() != null && !StringUtils.hasText(comment.getContent()))) {
            throw new InvalidRequestException();
        }
        Comment existing = lockComment(comment.get_id(), false);
        if (existing == null || !userId.equals(existing.getUserid())) {
            return false;
        }
        if (comment.getContent() != null) {
            existing.setContent(comment.getContent());
            commentDao.save(existing);
        }
        return true;
    }

    @Transactional
    public boolean deleteById(String id, String userId) {
        Comment existing = lockComment(id, false);
        if (existing == null || !userId.equals(existing.getUserid())) {
            return false;
        }
        List<String> ids = descendantIds(id, commentDao.findByArticleIdForUpdate(existing.getArticleid()));
        int deleted = 0;
        for (int start = 0; start < ids.size(); start += DELETE_BATCH_SIZE) {
            List<String> batch = ids.subList(start, Math.min(start + DELETE_BATCH_SIZE, ids.size()));
            commentThumbupDao.deleteByCommentIds(batch);
            deleted += commentDao.deleteComments(batch);
        }
        // Saturated historical counters may report zero changed rows; the article is already locked and present.
        articleDao.decrementComment(existing.getArticleid(), deleted);
        return true;
    }

    public List<Comment> findByarticleId(String articleId, String viewerId) {
        requireVisibleArticle(articleId, viewerId);
        return commentDao.findByArticleidOrderByPublishdateDesc(articleId);
    }

    private void requireVisibleArticle(String articleId, String viewerId) {
        Article article = articleDao.findById(articleId).orElseThrow(ResourceNotFoundException::new);
        if (!Article.STATE_PUBLISHED.equals(article.getState())
                && !(viewerId != null && (viewerId.equals(article.getUserid()) || isAdministrator(viewerId)))) {
            throw new ResourceNotFoundException();
        }
    }

    private boolean isAdministrator(String viewerId) {
        return viewerId != null && userRoleDao.countAdministrator(viewerId) > 0;
    }

    // All comment writes lock the article before the comment, including reply creation and branch deletion.
    private Comment lockComment(String id, boolean requiresPublishedArticle) {
        String articleId = commentDao.findArticleId(id).orElse(null);
        if (articleId == null) {
            return null;
        }
        Article article = articleDao.findByIdForUpdate(articleId).orElse(null);
        if (article == null || (requiresPublishedArticle && !Article.STATE_PUBLISHED.equals(article.getState()))) {
            return null;
        }
        return commentDao.findByIdForUpdate(id).orElse(null);
    }

    private List<String> descendantIds(String rootId, List<Comment> comments) {
        Map<String, List<String>> children = new HashMap<>();
        for (Comment comment : comments) {
            if (comment.getParentid() != null) {
                children.computeIfAbsent(comment.getParentid(), ignored -> new ArrayList<>()).add(comment.get_id());
            }
        }
        Set<String> ids = new LinkedHashSet<>();
        ArrayDeque<String> pending = new ArrayDeque<>();
        pending.add(rootId);
        while (!pending.isEmpty()) {
            String id = pending.removeFirst();
            if (ids.add(id)) {
                pending.addAll(children.getOrDefault(id, List.of()));
            }
        }
        return new ArrayList<>(ids);
    }

    @Transactional
    public ThumbupResult thumbup(String id, String userId) {
        Comment comment = lockComment(id, true);
        if (comment == null) {
            return ThumbupResult.NOT_FOUND;
        }
        String legacyKey = LEGACY_THUMBUP_PREFIX + userId + "_" + id;
        String cacheKey = THUMBUP_CACHE_PREFIX + userId + ":" + id;
        boolean legacyThumbup = hasRedisKey(legacyKey);
        if (commentThumbupDao.insertIfAbsent(id, userId) != 1) {
            return ThumbupResult.DUPLICATE;
        }
        if (legacyThumbup) {
            scheduleCache(cacheKey);
            return ThumbupResult.DUPLICATE;
        }
        if (commentDao.incrementThumbup(id) != 1) {
            commentThumbupDao.deleteClaim(id, userId);
            return ThumbupResult.NOT_FOUND;
        }
        scheduleCache(cacheKey);
        return ThumbupResult.SUCCESS;
    }

    private boolean hasRedisKey(String key) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private void cacheRedis(String key) {
        try {
            redisTemplate.opsForValue().setIfAbsent(key, 1, REDIS_CLAIM_TTL);
        } catch (RuntimeException exception) {
            // MySQL remains authoritative if the optional cache is unavailable.
        }
    }

    private void scheduleCache(String key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            cacheRedis(key);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cacheRedis(key);
            }
        });
    }

    public enum ThumbupResult {
        SUCCESS,
        DUPLICATE,
        NOT_FOUND
    }
}
