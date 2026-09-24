package com.liushao.article.dao;

import com.liushao.article.pojo.CommentThumbup;
import com.liushao.article.pojo.CommentThumbupId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentThumbupDao extends JpaRepository<CommentThumbup, CommentThumbupId> {
    @Modifying
    @Query(value = "INSERT IGNORE INTO tb_comment_thumbup (commentid, userid) "
            + "SELECT :commentId, :userId FROM tb_comment WHERE id = :commentId", nativeQuery = true)
    int insertIfAbsent(@Param("commentId") String commentId, @Param("userId") String userId);

    @Modifying
    @Query(value = "DELETE FROM tb_comment_thumbup WHERE commentid = :commentId AND userid = :userId", nativeQuery = true)
    int deleteClaim(@Param("commentId") String commentId, @Param("userId") String userId);
}