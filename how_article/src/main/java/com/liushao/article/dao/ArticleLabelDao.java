package com.liushao.article.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.liushao.article.pojo.ArticleLabel;
import com.liushao.article.pojo.ArticleLabelId;

public interface ArticleLabelDao extends JpaRepository<ArticleLabel, ArticleLabelId> {
    @Query(value = "SELECT labelid FROM tb_article_label WHERE articleid = :articleId ORDER BY labelid",
            nativeQuery = true)
    List<String> findLabelIdsByArticleId(@Param("articleId") String articleId);

    @Query(value = "SELECT labelid FROM tb_article_label WHERE articleid = :articleId ORDER BY labelid FOR UPDATE",
            nativeQuery = true)
    List<String> findLabelIdsByArticleIdForUpdate(@Param("articleId") String articleId);

    @Query(value = "SELECT articleid FROM tb_article_label WHERE labelid IN (:labelIds) "
            + "GROUP BY articleid HAVING COUNT(DISTINCT labelid) = :labelCount", nativeQuery = true)
    List<String> findArticleIdsWithAllLabels(@Param("labelIds") List<String> labelIds,
                                             @Param("labelCount") int labelCount);

    @Query(value = "SELECT COUNT(*) FROM tb_label WHERE id IN (:labelIds)", nativeQuery = true)
    long countExistingLabels(@Param("labelIds") List<String> labelIds);

    @Modifying
    @Query(value = "DELETE FROM tb_article_label WHERE articleid = :articleId", nativeQuery = true)
    int deleteByArticleId(@Param("articleId") String articleId);

    @Modifying
    @Query(value = "UPDATE tb_label SET count = COALESCE(count, 0) + 1 WHERE id IN (:labelIds)",
            nativeQuery = true)
    int incrementLabelCounts(@Param("labelIds") List<String> labelIds);

    @Modifying
    @Query(value = "UPDATE tb_label SET count = GREATEST(COALESCE(count, 0) - 1, 0) WHERE id IN (:labelIds)",
            nativeQuery = true)
    int decrementLabelCounts(@Param("labelIds") List<String> labelIds);

    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT IGNORE INTO tb_article_label (articleid, labelid) "
            + "SELECT :articleId, id FROM tb_label WHERE id IN (:labelIds)", nativeQuery = true)
    int insertExistingLabels(@Param("articleId") String articleId, @Param("labelIds") List<String> labelIds);
}
