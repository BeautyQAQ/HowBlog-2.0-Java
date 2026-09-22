package com.liushao.article.service;

import com.liushao.article.dao.ArticleDao;
import com.liushao.article.pojo.Article;
import com.liushao.util.IdWorker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.persistence.criteria.Predicate;

@Service
public class ArticleService {

    @Autowired
    private ArticleDao articleDao;
    @Autowired
    private IdWorker idWorker;

    public List<Article> findAll() {
        return articleDao.findAll();
    }

    public Article findById(String id) {
        return articleDao.findById(id).orElse(null);
    }

    public void add(Article article, String userId) {
        article.setId(idWorker.nextId() + "");
        article.setUserid(userId);
        articleDao.save(article);
    }

    public boolean update(Article article, String userId) {
        return articleDao.findById(article.getId())
                .filter(existing -> userId.equals(existing.getUserid()))
                .map(existing -> {
            if (article.getColumnid() != null) existing.setColumnid(article.getColumnid());
            if (article.getTitle() != null) existing.setTitle(article.getTitle());
            if (article.getContent() != null) existing.setContent(article.getContent());
            if (article.getImage() != null) existing.setImage(article.getImage());
            if (article.getIspublic() != null) existing.setIspublic(article.getIspublic());
            if (article.getChannelid() != null) existing.setChannelid(article.getChannelid());
            if (article.getUrl() != null) existing.setUrl(article.getUrl());
            if (article.getType() != null) existing.setType(article.getType());
            existing.setUpdatetime(new Date());
            articleDao.save(existing);
            return true;
        }).orElse(false);
    }

    public boolean delete(String id, String userId) {
        return articleDao.findById(id)
                .filter(article -> userId.equals(article.getUserid()))
                .map(article -> {
                    articleDao.delete(article);
                    return true;
                })
                .orElse(false);
    }

    public Page<Article> search(Map<String, Object> conditions, int page, int size) {
        Specification<Article> specification = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            for (Map.Entry<String, Object> entry : conditions.entrySet()) {
                if (entry.getValue() != null && isArticleField(entry.getKey())) {
                    predicates.add(builder.equal(root.get(entry.getKey()), entry.getValue()));
                }
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
        int pageNumber = Math.max(page - 1, 0);
        int pageSize = Math.max(size, 1);
        return articleDao.findAll(specification, PageRequest.of(pageNumber, pageSize));
    }

    private boolean isArticleField(String field) {
        return switch (field) {
            case "id", "columnid", "userid", "title", "content", "image", "createtime", "updatetime",
                    "ispublic", "istop", "visits", "thumbup", "comment", "state", "channelid", "url", "type" -> true;
            default -> false;
        };
    }
}
