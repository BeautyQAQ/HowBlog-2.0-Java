package com.liushao.article.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.liushao.article.pojo.Comment;

/**
 * @author huangshen
 */
public interface CommentDao extends JpaRepository<Comment, String> {
    List<Comment> findByArticleidOrderByPublishdateDesc(String articleId);

    @Query(value = "SELECT * FROM tb_comment WHERE articleid = :articleId ORDER BY id FOR UPDATE", nativeQuery = true)
    List<Comment> findByArticleIdForUpdate(@Param("articleId") String articleId);

    @Query(value = "SELECT articleid FROM tb_comment WHERE id = :id", nativeQuery = true)
    Optional<String> findArticleId(@Param("id") String id);

    @Query(value = "SELECT c.* FROM tb_comment c JOIN tb_article a ON a.id = c.articleid "
            + "WHERE a.state = '1' OR a.userid = :viewerId OR :administrator = true "
            + "ORDER BY c.publishdate DESC, c.id DESC", nativeQuery = true)
    List<Comment> findVisible(@Param("viewerId") String viewerId, @Param("administrator") boolean administrator);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM tb_comment WHERE articleid = :articleId", nativeQuery = true)
    int deleteByArticleidBulk(@Param("articleId") String articleId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM tb_comment WHERE id IN (:ids)", nativeQuery = true)
    int deleteComments(@Param("ids") List<String> ids);

    @Query(value = "SELECT * FROM tb_comment WHERE id = :id FOR UPDATE", nativeQuery = true)
    Optional<Comment> findByIdForUpdate(@Param("id") String id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE tb_comment SET thumbup = thumbup + 1 WHERE id = :id", nativeQuery = true)
    int incrementThumbup(@Param("id") String id);
}
