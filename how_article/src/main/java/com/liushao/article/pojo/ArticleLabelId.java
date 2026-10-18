package com.liushao.article.pojo;

import java.io.Serializable;
import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Embeddable;

@Embeddable
public class ArticleLabelId implements Serializable {
    @Column(name = "articleid")
    private String articleId;

    @Column(name = "labelid")
    private String labelId;

    protected ArticleLabelId() {
    }

    public ArticleLabelId(String articleId, String labelId) {
        this.articleId = articleId;
        this.labelId = labelId;
    }

    public String getArticleId() {
        return articleId;
    }

    public String getLabelId() {
        return labelId;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof ArticleLabelId that)) return false;
        return Objects.equals(articleId, that.articleId) && Objects.equals(labelId, that.labelId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(articleId, labelId);
    }
}