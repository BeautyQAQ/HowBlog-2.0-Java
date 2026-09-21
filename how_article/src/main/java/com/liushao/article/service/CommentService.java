package com.liushao.article.service;

import com.baomidou.mybatisplus.mapper.EntityWrapper;
import com.liushao.article.dao.CommentDao;
import com.liushao.article.pojo.Comment;
import com.liushao.util.IdWorker;
import org.springframework.beans.factory.annotation.Autowired;
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
        return commentDao.selectById(id);
    }

    public List<Comment> findAll() {
        return commentDao.selectList(null);
    }

    public void save(Comment comment) {
        String id = idWorker.nextId() + "";
        comment.set_id(id);

        //初始化数据
        comment.setPublishdate(new Date());
        comment.setThumbup(0);

        commentDao.insert(comment);
    }

    public void update(Comment comment) {
        commentDao.updateById(comment);
    }

    public void deleteById(String id) {
        commentDao.deleteById(id);
    }

    /**
     * 根据文章id查询评论
     */
    public List<Comment> findByarticleId(String articleId) {
        EntityWrapper<Comment> wrapper = new EntityWrapper<>();
        wrapper.eq("articleid", articleId);
        wrapper.orderBy("publishdate", false);
        return commentDao.selectList(wrapper);
    }

    /**
     * 点赞
     */
    public void thumbup(String id) {
        commentDao.incrementThumbup(id);
    }
}
