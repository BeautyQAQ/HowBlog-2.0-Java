package com.liushao.article.pojo;

import java.io.Serializable;
import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Embeddable;

@Embeddable
public class ArticleThumbupId implements Serializable {
    @Column(name = "articleid")
    private String articleId;

    @Column(name = "userid")
    private String userId;

    protected ArticleThumbupId() {
    }

    public ArticleThumbupId(String articleId, String userId) {
        this.articleId = articleId;
        this.userId = userId;
    }

    public String getArticleId() {
        return articleId;
    }

    public String getUserId() {
        return userId;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof ArticleThumbupId that)) return false;
        return Objects.equals(articleId, that.articleId) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(articleId, userId);
    }
}
