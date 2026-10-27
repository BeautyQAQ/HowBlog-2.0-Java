package com.liushao.article.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.liushao.article.pojo.Article;

/**
 * @author huangshen
 */
public interface ArticleDao extends JpaRepository<Article, String>, JpaSpecificationExecutor<Article> {

    @Query("SELECT a FROM Article a WHERE a.state = '1' AND (a.ispublic IS NULL OR a.ispublic = '1')")
    List<Article> findPublicArticles(Sort sort);

    // 实体写入和统计增量共用文章行锁，防止整行保存覆盖并发计数。
    @Query(value = "SELECT * FROM tb_article WHERE id = :id FOR UPDATE", nativeQuery = true)
    Optional<Article> findByIdForUpdate(@Param("id") String id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(value = "UPDATE tb_article SET visits = visits + 1 WHERE id = :id", nativeQuery = true)
    int incrementVisits(@Param("id") String id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE tb_article SET thumbup = thumbup + 1 WHERE id = :id", nativeQuery = true)
    int incrementThumbup(@Param("id") String id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE tb_article SET comment = COALESCE(comment, 0) + 1 WHERE id = :id", nativeQuery = true)
    int incrementComment(@Param("id") String id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE tb_article SET comment = GREATEST(COALESCE(comment, 0) - :amount, 0) "
            + "WHERE id = :id", nativeQuery = true)
    int decrementComment(@Param("id") String id, @Param("amount") int amount);
}
