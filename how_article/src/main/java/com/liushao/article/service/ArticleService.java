package com.liushao.article.service;

import com.liushao.article.dao.ArticleDao;
import com.liushao.article.dao.ArticleLabelDao;
import com.liushao.article.dao.ArticleThumbupDao;
import com.liushao.article.dao.UserRoleDao;
import com.liushao.article.pojo.Article;
import com.liushao.util.IdWorker;
import com.liushao.web.ForbiddenException;
import com.liushao.web.InvalidRequestException;
import com.liushao.web.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.persistence.criteria.Predicate;

@Service
public class ArticleService {

    private final ArticleDao articleDao;
    private final IdWorker idWorker;
    private final UserRoleDao userRoleDao;
    private final ArticleThumbupDao articleThumbupDao;
    private final ArticleLabelDao articleLabelDao;

    public ArticleService(ArticleDao articleDao, IdWorker idWorker, UserRoleDao userRoleDao,
                          ArticleThumbupDao articleThumbupDao, ArticleLabelDao articleLabelDao) {
        this.articleDao = articleDao;
        this.idWorker = idWorker;
        this.userRoleDao = userRoleDao;
        this.articleThumbupDao = articleThumbupDao;
        this.articleLabelDao = articleLabelDao;
    }

    public List<Article> findAll() {
        List<Article> articles = articleDao.findByState(Article.STATE_PUBLISHED);
        articles.forEach(this::populateLabels);
        return articles;
    }

    /**
     * 按 ID 查询文章。已发布文章公开可见；草稿与待审核文章仅作者本人或管理员可见，
     * 其他访问者按资源不存在处理。非作者读取已发布文章时累加一次浏览量。
     */
    public Article findById(String id, String viewerId) {
        Article article = articleDao.findById(id).orElseThrow(ResourceNotFoundException::new);
        boolean visible = Article.STATE_PUBLISHED.equals(article.getState())
                || (viewerId != null && (viewerId.equals(article.getUserid()) || isAdministrator(viewerId)));
        if (!visible) {
            throw new ResourceNotFoundException();
        }
        if (Article.STATE_PUBLISHED.equals(article.getState())
                && !Objects.equals(article.getUserid(), viewerId)
                && articleDao.incrementVisits(id) == 1) {
            article.setVisits(article.getVisits() == null ? 1 : article.getVisits() + 1);
        }
        populateLabels(article);
        return article;
    }

    /**
     * 新增文章。文章以草稿创建，生命周期与统计字段由服务端维护，忽略请求体中的同名值。
     */
    @Transactional
    public void add(Article article, String userId) {
        List<String> labelIds = normalizeLabelIds(article.getLabelIds());
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
        if (labelIds != null) {
            replaceLabels(article.getId(), labelIds);
        }
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

    /**
     * 提交审核：作者将草稿提交，等待管理员处理。
     */
    public TransitionResult submit(String id, String userId) {
        return articleDao.findById(id)
                .filter(article -> userId.equals(article.getUserid()))
                .map(article -> {
                    if (!Article.STATE_DRAFT.equals(article.getState())) {
                        return TransitionResult.INVALID_STATE;
                    }
                    article.setState(Article.STATE_PENDING);
                    article.setUpdatetime(new Date());
                    articleDao.save(article);
                    return TransitionResult.SUCCESS;
                }).orElse(TransitionResult.NOT_ALLOWED);
    }

    /**
     * 审核通过：管理员将待审核文章置为已发布。
     */
    public TransitionResult approve(String id, String userId) {
        requireAdministrator(userId);
        return review(id, true);
    }

    /**
     * 审核驳回：管理员将待审核文章退回草稿。
     */
    public TransitionResult reject(String id, String userId) {
        requireAdministrator(userId);
        return review(id, false);
    }

    private TransitionResult review(String id, boolean approve) {
        Article article = articleDao.findById(id).orElseThrow(ResourceNotFoundException::new);
        if (!Article.STATE_PENDING.equals(article.getState())) {
            return TransitionResult.INVALID_STATE;
        }
        if (approve) {
            if (article.getCreatetime() == null) {
                article.setCreatetime(new Date());
            }
            article.setState(Article.STATE_PUBLISHED);
        } else {
            article.setState(Article.STATE_DRAFT);
        }
        article.setUpdatetime(new Date());
        articleDao.save(article);
        return TransitionResult.SUCCESS;
    }

    /**
     * 文章点赞：仅已发布文章可点赞；先登记唯一关系，再对计数做数据库端原子加 1。
     * 同一用户重复点赞返回 DUPLICATE 且不计数；文章不存在或非已发布返回 NOT_FOUND。
     */
    @Transactional
    public ThumbupResult thumbup(String id, String userId) {
        if (articleDao.findByIdForUpdate(id)
                .filter(article -> Article.STATE_PUBLISHED.equals(article.getState()))
                .isEmpty()) {
            return ThumbupResult.NOT_FOUND;
        }
        if (articleThumbupDao.insertIfAbsent(id, userId) != 1) {
            return ThumbupResult.DUPLICATE;
        }
        if (articleDao.incrementThumbup(id) != 1) {
            articleThumbupDao.deleteClaim(id, userId);
            return ThumbupResult.NOT_FOUND;
        }
        return ThumbupResult.SUCCESS;
    }

    @Transactional
    public boolean update(Article article, String userId) {
        List<String> labelIds = normalizeLabelIds(article.getLabelIds());
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
            if (labelIds != null) {
                replaceLabels(existing.getId(), labelIds);
            }
            return true;
        }).orElse(false);
    }

    @Transactional
    public boolean delete(String id, String userId) {
        return articleDao.findById(id)
                .filter(article -> userId.equals(article.getUserid()))
                .map(article -> {
                    replaceLabels(id, List.of());
                    articleDao.delete(article);
                    return true;
                })
                .orElse(false);
    }

    public Page<Article> search(Map<String, Object> conditions, int page, int size, String viewerId) {
        if (conditions == null || page < 1 || size < 1 || size > 100) {
            throw new InvalidRequestException();
        }
        List<String> labelIds = normalizeLabelIds(readLabelIds(conditions.get("labelIds")));
        List<String> matchingArticleIds = labelIds == null || labelIds.isEmpty()
                ? null
                : articleLabelDao.findArticleIdsWithAllLabels(labelIds, labelIds.size());
        boolean administrator = isAdministrator(viewerId);
        Specification<Article> specification = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            for (Map.Entry<String, Object> entry : conditions.entrySet()) {
                if (entry.getValue() != null && isArticleField(entry.getKey())) {
                    predicates.add(builder.equal(root.get(entry.getKey()), entry.getValue()));
                }
            }
            if (matchingArticleIds != null) {
                predicates.add(matchingArticleIds.isEmpty()
                        ? builder.disjunction()
                        : root.get("id").in(matchingArticleIds));
            }
            if (!administrator) {
                Predicate published = builder.equal(root.get("state"), Article.STATE_PUBLISHED);
                if (viewerId == null) {
                    predicates.add(published);
                } else {
                    predicates.add(builder.or(published, builder.equal(root.get("userid"), viewerId)));
                }
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
        Page<Article> result = articleDao.findAll(specification, PageRequest.of(page - 1, size));
        result.forEach(this::populateLabels);
        return result;
    }

    private void populateLabels(Article article) {
        article.setLabelIds(articleLabelDao.findLabelIdsByArticleId(article.getId()));
    }

    private void replaceLabels(String articleId, List<String> labelIds) {
        List<String> existingLabelIds = articleLabelDao.findLabelIdsByArticleId(articleId);
        if (existingLabelIds == null) {
            existingLabelIds = List.of();
        }
        if (existingLabelIds.equals(labelIds)) {
            return;
        }
        if (!existingLabelIds.isEmpty()) {
            articleLabelDao.decrementLabelCounts(existingLabelIds);
        }
        articleLabelDao.deleteByArticleId(articleId);
        if (!labelIds.isEmpty()) {
            articleLabelDao.insertExistingLabels(articleId, labelIds);
            articleLabelDao.incrementLabelCounts(labelIds);
        }
    }

    private List<String> readLabelIds(Object value) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof List<?> values)) {
            throw new InvalidRequestException();
        }
        List<String> labelIds = new ArrayList<>();
        for (Object item : values) {
            if (!(item instanceof String labelId)) {
                throw new InvalidRequestException();
            }
            labelIds.add(labelId);
        }
        return labelIds;
    }

    private List<String> normalizeLabelIds(List<String> labelIds) {
        if (labelIds == null) {
            return null;
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String labelId : labelIds) {
            if (labelId == null || labelId.isBlank()) {
                throw new InvalidRequestException();
            }
            normalized.add(labelId.trim());
        }
        List<String> result = new ArrayList<>(normalized);
        Collections.sort(result);
        if (!result.isEmpty() && articleLabelDao.countExistingLabels(result) != result.size()) {
            throw new InvalidRequestException();
        }
        return result;
    }

    private boolean isArticleField(String field) {
        return switch (field) {
            case "id", "columnid", "userid", "title", "content", "image", "createtime", "updatetime",
                    "ispublic", "istop", "visits", "thumbup", "comment", "state", "channelid", "url", "type" -> true;
            default -> false;
        };
    }

    private void requireAdministrator(String userId) {
        if (!isAdministrator(userId)) {
            throw new ForbiddenException();
        }
    }

    private boolean isAdministrator(String userId) {
        return userId != null && userRoleDao.countAdministrator(userId) > 0;
    }

    public enum TransitionResult {
        SUCCESS,
        NOT_ALLOWED,
        INVALID_STATE
    }

    public enum ThumbupResult {
        SUCCESS,
        DUPLICATE,
        NOT_FOUND
    }
}
