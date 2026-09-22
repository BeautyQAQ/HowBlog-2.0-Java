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

    public void add(Article article) {
        article.setId(idWorker.nextId() + "");
        articleDao.save(article);
    }

    public void update(Article article) {
        articleDao.findById(article.getId()).ifPresent(existing -> {
            if (article.getColumnid() != null) existing.setColumnid(article.getColumnid());
            if (article.getUserid() != null) existing.setUserid(article.getUserid());
            if (article.getTitle() != null) existing.setTitle(article.getTitle());
            if (article.getContent() != null) existing.setContent(article.getContent());
            if (article.getImage() != null) existing.setImage(article.getImage());
            if (article.getCreatetime() != null) existing.setCreatetime(article.getCreatetime());
            if (article.getUpdatetime() != null) existing.setUpdatetime(article.getUpdatetime());
            if (article.getIspublic() != null) existing.setIspublic(article.getIspublic());
            if (article.getIstop() != null) existing.setIstop(article.getIstop());
            if (article.getVisits() != null) existing.setVisits(article.getVisits());
            if (article.getThumbup() != null) existing.setThumbup(article.getThumbup());
            if (article.getComment() != null) existing.setComment(article.getComment());
            if (article.getState() != null) existing.setState(article.getState());
            if (article.getChannelid() != null) existing.setChannelid(article.getChannelid());
            if (article.getUrl() != null) existing.setUrl(article.getUrl());
            if (article.getType() != null) existing.setType(article.getType());
            articleDao.save(existing);
        });
    }

    public void delete(String id) {
        if (articleDao.existsById(id)) {
            articleDao.deleteById(id);
        }
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
