package com.liushao.article.pojo;

import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Table(name = "tb_article_label")
public class ArticleLabel {
    @EmbeddedId
    private ArticleLabelId id;

    protected ArticleLabel() {
    }

    public ArticleLabel(ArticleLabelId id) {
        this.id = id;
    }

    public ArticleLabelId getId() {
        return id;
    }
}