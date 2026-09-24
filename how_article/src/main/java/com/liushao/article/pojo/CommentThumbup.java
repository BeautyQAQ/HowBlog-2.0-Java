package com.liushao.article.pojo;

import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Table(name = "tb_comment_thumbup")
public class CommentThumbup {
    @EmbeddedId
    private CommentThumbupId id;

    protected CommentThumbup() {
    }

    public CommentThumbup(CommentThumbupId id) {
        this.id = id;
    }

    public CommentThumbupId getId() {
        return id;
    }
}