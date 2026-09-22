package com.liushao.article.service;

import com.liushao.article.dao.CommentDao;
import com.liushao.article.pojo.Comment;
import com.liushao.util.IdWorker;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * @author huangshen
 */
@Service
public class CommentService {
    private final IdWorker idWorker;
    private final CommentDao commentDao;
    private final RedisTemplate redisTemplate;

    public CommentService(IdWorker idWorker, CommentDao commentDao, RedisTemplate redisTemplate) {
        this.idWorker = idWorker;
        this.commentDao = commentDao;
        this.redisTemplate = redisTemplate;
    }

    public Comment findById(String id) {
        return commentDao.findById(id).orElse(null);
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
        String key = "thumbup_" + userId + "_" + id;
        Boolean claimed = redisTemplate.opsForValue().setIfAbsent(key, 1);
        if (!Boolean.TRUE.equals(claimed)) {
            return ThumbupResult.DUPLICATE;
        }
        try {
            if (commentDao.incrementThumbup(id) != 1) {
                redisTemplate.delete(key);
                return ThumbupResult.NOT_FOUND;
            }
            return ThumbupResult.SUCCESS;
        } catch (RuntimeException exception) {
            redisTemplate.delete(key);
            throw exception;
        }
    }

    public enum ThumbupResult {
        SUCCESS,
        DUPLICATE,
        NOT_FOUND
    }
}
