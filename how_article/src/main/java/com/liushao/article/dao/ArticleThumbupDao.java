package com.liushao.article.dao;

import com.liushao.article.pojo.ArticleThumbup;
import com.liushao.article.pojo.ArticleThumbupId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 文章点赞唯一关系；INSERT IGNORE ... SELECT 保证文章存在且同一用户只登记一次。
 */
public interface ArticleThumbupDao extends JpaRepository<ArticleThumbup, ArticleThumbupId> {
    @Modifying
    @Query(value = "INSERT IGNORE INTO tb_article_thumbup (articleid, userid) "
            + "SELECT :articleId, :userId FROM tb_article WHERE id = :articleId", nativeQuery = true)
    int insertIfAbsent(@Param("articleId") String articleId, @Param("userId") String userId);

    @Modifying
    @Query(value = "DELETE FROM tb_article_thumbup WHERE articleid = :articleId AND userid = :userId", nativeQuery = true)
    int deleteClaim(@Param("articleId") String articleId, @Param("userId") String userId);
}
