package com.liushao.article.dao;

import com.baomidou.mybatisplus.mapper.BaseMapper;
import com.liushao.article.pojo.Comment;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * @author huangshen
 */
public interface CommentDao extends BaseMapper<Comment> {
    @Select("SELECT * FROM tb_comment WHERE articleid = #{articleId} ORDER BY publishdate DESC")
    List<Comment> selectByArticleid(@Param("articleId") String articleId);

    @Update("UPDATE tb_comment SET thumbup = thumbup + 1 WHERE id = #{id}")
    int incrementThumbup(@Param("id") String id);
}