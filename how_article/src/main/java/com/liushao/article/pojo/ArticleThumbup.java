package com.liushao.article.pojo;

import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Table(name = "tb_article_thumbup")
public class ArticleThumbup {
    @EmbeddedId
    private ArticleThumbupId id;

    protected ArticleThumbup() {
    }

    public ArticleThumbup(ArticleThumbupId id) {
        this.id = id;
    }

    public ArticleThumbupId getId() {
        return id;
    }
}
