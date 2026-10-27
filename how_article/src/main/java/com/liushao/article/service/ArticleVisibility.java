package com.liushao.article.service;

import com.liushao.article.pojo.Article;

final class ArticleVisibility {
    private ArticleVisibility() {
    }

    static boolean isPublic(Article article) {
        return Article.STATE_PUBLISHED.equals(article.getState())
                && (article.getIspublic() == null || "1".equals(article.getIspublic()));
    }
}
