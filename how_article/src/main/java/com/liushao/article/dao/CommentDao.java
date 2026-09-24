package com.liushao.article.dao;

import com.liushao.article.pojo.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * @author huangshen
 */
public interface CommentDao extends JpaRepository<Comment, String> {
    List<Comment> findByArticleidOrderByPublishdateDesc(String articleId);

    @Query(value = "SELECT * FROM tb_comment WHERE id = :id FOR UPDATE", nativeQuery = true)
    Optional<Comment> findByIdForUpdate(@Param("id") String id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE tb_comment SET thumbup = thumbup + 1 WHERE id = :id", nativeQuery = true)
    int incrementThumbup(@Param("id") String id);
}