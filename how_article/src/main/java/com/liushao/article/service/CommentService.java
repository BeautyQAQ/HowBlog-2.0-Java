package com.liushao.article.service;

import com.liushao.article.dao.CommentDao;
import com.liushao.article.pojo.Comment;
import com.liushao.util.IdWorker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * @author huangshen
 */
@Service
public class CommentService {
    @Autowired
    private IdWorker idWorker;
    @Autowired
    private CommentDao commentDao;

    public Comment findById(String id) {
        return commentDao.findById(id).orElse(null);
    }

    public List<Comment> findAll() {
        return commentDao.findAll();
    }

    public void save(Comment comment) {
        String id = idWorker.nextId() + "";
        comment.set_id(id);

        //初始化数据
        comment.setPublishdate(new Date());
        comment.setThumbup(0);

        commentDao.save(comment);
    }

    public void update(Comment comment) {
        commentDao.findById(comment.get_id()).ifPresent(existing -> {
            if (comment.getArticleid() != null) existing.setArticleid(comment.getArticleid());
            if (comment.getContent() != null) existing.setContent(comment.getContent());
            if (comment.getUserid() != null) existing.setUserid(comment.getUserid());
            if (comment.getParentid() != null) existing.setParentid(comment.getParentid());
            if (comment.getPublishdate() != null) existing.setPublishdate(comment.getPublishdate());
            if (comment.getThumbup() != null) existing.setThumbup(comment.getThumbup());
            commentDao.save(existing);
        });
    }

    public void deleteById(String id) {
        if (commentDao.existsById(id)) {
            commentDao.deleteById(id);
        }
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
    public void thumbup(String id) {
        commentDao.incrementThumbup(id);
    }
}
