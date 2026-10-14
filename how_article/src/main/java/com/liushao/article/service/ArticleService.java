package com.liushao.article.service;

import com.liushao.article.dao.ArticleDao;
import com.liushao.article.pojo.Article;
import com.liushao.util.IdWorker;
import com.liushao.web.InvalidRequestException;
import com.liushao.web.ResourceNotFoundException;
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

    private final ArticleDao articleDao;
    private final IdWorker idWorker;

    public ArticleService(ArticleDao articleDao, IdWorker idWorker) {
        this.articleDao = articleDao;
        this.idWorker = idWorker;
    }

    public List<Article> findAll() {
        return articleDao.findByState(Article.STATE_PUBLISHED);
    }

    /**
     * 按 ID 查询文章。草稿仅作者本人可见，其他访问者按资源不存在处理。
     */
    public Article findById(String id, String viewerId) {
        Article article = articleDao.findById(id).orElseThrow(ResourceNotFoundException::new);
        if (!Article.STATE_PUBLISHED.equals(article.getState())
                && (viewerId == null || !viewerId.equals(article.getUserid()))) {
            throw new ResourceNotFoundException();
        }
        return article;
    }

    /**
     * 新增文章。文章以草稿创建，生命周期与统计字段由服务端维护，忽略请求体中的同名值。
     */
    public void add(Article article, String userId) {
        article.setId(idWorker.nextId() + "");
        article.setUserid(userId);
        article.setState(Article.STATE_DRAFT);
        article.setCreatetime(null);
        article.setUpdatetime(new Date());
        article.setIstop("0");
        article.setVisits(0);
        article.setThumbup(0);
        article.setComment(0);
        articleDao.save(article);
    }

    /**
     * 发布草稿：状态置为已发布，首次发布记录发布时间。
     */
    public boolean publish(String id, String userId) {
        return articleDao.findById(id)
                .filter(article -> userId.equals(article.getUserid()))
                .map(article -> {
                    if (article.getCreatetime() == null) {
                        article.setCreatetime(new Date());
                    }
                    article.setState(Article.STATE_PUBLISHED);
                    article.setUpdatetime(new Date());
                    articleDao.save(article);
                    return true;
                }).orElse(false);
    }

    /**
     * 下架文章：状态退回草稿，保留发布时间。
     */
    public boolean unpublish(String id, String userId) {
        return articleDao.findById(id)
                .filter(article -> userId.equals(article.getUserid()))
                .map(article -> {
                    article.setState(Article.STATE_DRAFT);
                    article.setUpdatetime(new Date());
                    articleDao.save(article);
                    return true;
                }).orElse(false);
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

    public Page<Article> search(Map<String, Object> conditions, int page, int size, String viewerId) {
        if (conditions == null || page < 1 || size < 1 || size > 100) {
            throw new InvalidRequestException();
        }
        Specification<Article> specification = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            for (Map.Entry<String, Object> entry : conditions.entrySet()) {
                if (entry.getValue() != null && isArticleField(entry.getKey())) {
                    predicates.add(builder.equal(root.get(entry.getKey()), entry.getValue()));
                }
            }
            Predicate published = builder.equal(root.get("state"), Article.STATE_PUBLISHED);
            if (viewerId == null) {
                predicates.add(published);
            } else {
                predicates.add(builder.or(published, builder.equal(root.get("userid"), viewerId)));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
        return articleDao.findAll(specification, PageRequest.of(page - 1, size));
    }

    private boolean isArticleField(String field) {
        return switch (field) {
            case "id", "columnid", "userid", "title", "content", "image", "createtime", "updatetime",
                    "ispublic", "istop", "visits", "thumbup", "comment", "state", "channelid", "url", "type" -> true;
            default -> false;
        };
    }
}
