package com.liushao.article.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.liushao.article.pojo.CommentThumbup;
import com.liushao.article.pojo.CommentThumbupId;

public interface CommentThumbupDao extends JpaRepository<CommentThumbup, CommentThumbupId> {
    @Modifying
    @Query(value = "INSERT IGNORE INTO tb_comment_thumbup (commentid, userid) "
            + "SELECT :commentId, :userId FROM tb_comment WHERE id = :commentId", nativeQuery = true)
    int insertIfAbsent(@Param("commentId") String commentId, @Param("userId") String userId);

    @Modifying
    @Query(value = "DELETE FROM tb_comment_thumbup WHERE commentid = :commentId AND userid = :userId", nativeQuery = true)
    int deleteClaim(@Param("commentId") String commentId, @Param("userId") String userId);

    @Modifying(flushAutomatically = true)
    @Query(value = "DELETE FROM tb_comment_thumbup WHERE commentid IN "
            + "(SELECT id FROM tb_comment WHERE articleid = :articleId)", nativeQuery = true)
    int deleteByArticleId(@Param("articleId") String articleId);

    @Modifying(flushAutomatically = true)
    @Query(value = "DELETE FROM tb_comment_thumbup WHERE commentid IN (:ids)", nativeQuery = true)
    int deleteByCommentIds(@Param("ids") List<String> ids);
}
