package com.liushao.article.dao;

import com.liushao.article.pojo.Article;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * @author huangshen
 */
public interface ArticleDao extends JpaRepository<Article, String>, JpaSpecificationExecutor<Article> {

    List<Article> findByState(String state);

    @Query(value = "SELECT * FROM tb_article WHERE id = :id FOR UPDATE", nativeQuery = true)
    Optional<Article> findByIdForUpdate(@Param("id") String id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(value = "UPDATE tb_article SET visits = visits + 1 WHERE id = :id", nativeQuery = true)
    int incrementVisits(@Param("id") String id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE tb_article SET thumbup = thumbup + 1 WHERE id = :id", nativeQuery = true)
    int incrementThumbup(@Param("id") String id);
}
