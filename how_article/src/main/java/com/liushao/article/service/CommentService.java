package com.liushao.article.service;

import com.liushao.article.dao.CommentDao;
import com.liushao.article.dao.CommentThumbupDao;
import com.liushao.article.pojo.Comment;
import com.liushao.util.IdWorker;
import com.liushao.web.ResourceNotFoundException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Date;
import java.util.List;

/**
 * @author huangshen
 */
@Service
public class CommentService {
    private static final Duration REDIS_CLAIM_TTL = Duration.ofHours(1);
    private static final String LEGACY_THUMBUP_PREFIX = "thumbup_";
    private static final String THUMBUP_CACHE_PREFIX = "thumbup:v2:";

    private final IdWorker idWorker;
    private final CommentDao commentDao;
    private final CommentThumbupDao commentThumbupDao;
    private final RedisTemplate redisTemplate;

    public CommentService(IdWorker idWorker, CommentDao commentDao, CommentThumbupDao commentThumbupDao,
                          RedisTemplate redisTemplate) {
        this.idWorker = idWorker;
        this.commentDao = commentDao;
        this.commentThumbupDao = commentThumbupDao;
        this.redisTemplate = redisTemplate;
    }

    public Comment findById(String id) {
        return commentDao.findById(id).orElseThrow(ResourceNotFoundException::new);
    }

    public List<Comment> findAll() {
        return commentDao.findAll();
    }

    public void save(Comment comment, String userId) {
        String id = idWorker.nextId() + "";
        comment.set_id(id);
        comment.setUserid(userId);

        //初始化数据
        comment.setPublishdate(new Date());
        comment.setThumbup(0);

        commentDao.save(comment);
    }

    public boolean update(Comment comment, String userId) {
        return commentDao.findById(comment.get_id())
                .filter(existing -> userId.equals(existing.getUserid()))
                .map(existing -> {
            if (comment.getContent() != null) existing.setContent(comment.getContent());
            commentDao.save(existing);
            return true;
        }).orElse(false);
    }

    public boolean deleteById(String id, String userId) {
        return commentDao.findById(id)
                .filter(comment -> userId.equals(comment.getUserid()))
                .map(comment -> {
                    commentDao.delete(comment);
                    return true;
                })
                .orElse(false);
    }

    /**
     * 根据文章id查询评论
     */
    public List<Comment> findByarticleId(String articleId) {
        return commentDao.findByArticleidOrderByPublishdateDesc(articleId);
    }

    /**
     * 点赞
     */
    @Transactional
    public ThumbupResult thumbup(String id, String userId) {
        String legacyKey = LEGACY_THUMBUP_PREFIX + userId + "_" + id;
        String cacheKey = THUMBUP_CACHE_PREFIX + userId + ":" + id;
        try {
            if (commentDao.findByIdForUpdate(id).isEmpty()) {
                return ThumbupResult.NOT_FOUND;
            }
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
        } catch (RuntimeException exception) {
            throw exception;
        }
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

    private void releaseRedis(String key) {
        try {
            redisTemplate.delete(key);
        } catch (RuntimeException ignored) {
        }
    }

    public enum ThumbupResult {
        SUCCESS,
        DUPLICATE,
        NOT_FOUND
    }
}
