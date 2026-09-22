package com.liushao.article.controller;

import com.liushao.auth.CurrentUserContext;
import com.liushao.auth.RequireAuthentication;
import com.liushao.article.pojo.Comment;
import com.liushao.article.service.CommentService;
import com.liushao.entity.Result;
import com.liushao.entity.StatusCode;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;

import com.liushao.web.ResourceNotFoundException;
import com.liushao.web.ValidationGroups;

import java.util.List;

/**
 * @author huangshen
 */
@RestController
@RequestMapping("comment")
@CrossOrigin
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    //根据id查询评论
    @RequestMapping(value = "{id}", method = RequestMethod.GET)
    public Result findById(@PathVariable String id) {
        Comment comment = commentService.findById(id);
        return new Result(true, StatusCode.OK, "查询成功", comment);
    }

    //查询所有
    @RequestMapping(method = RequestMethod.GET)
    public Result findAll() {
        List<Comment> list = commentService.findAll();
        return new Result(true, StatusCode.OK, "查询成功", list);
    }

    //新增
    @RequestMapping(method = RequestMethod.POST)
    @RequireAuthentication
    public Result save(@Validated(ValidationGroups.Create.class) @RequestBody Comment comment) {
        commentService.save(comment, CurrentUserContext.require().getUserId());
        return new Result(true, StatusCode.OK, "新增成功");
    }

    //修改
    @RequestMapping(value = "{id}", method = RequestMethod.PUT)
    @RequireAuthentication
    public Result update(@PathVariable String id,
                         @Validated(ValidationGroups.Update.class) @RequestBody Comment comment) {
        comment.set_id(id);
        if (!commentService.update(comment, CurrentUserContext.require().getUserId())) {
            return new Result(false, StatusCode.ACCESSERROR, "无权操作或评论不存在");
        }
        return new Result(true, StatusCode.OK, "修改成功");
    }

    //删除
    @RequestMapping(value = "{id}", method = RequestMethod.DELETE)
    @RequireAuthentication
    public Result deleteById(@PathVariable String id) {
        if (!commentService.deleteById(id, CurrentUserContext.require().getUserId())) {
            return new Result(false, StatusCode.ACCESSERROR, "无权操作或评论不存在");
        }
        return new Result(true, StatusCode.OK, "删除成功");
    }

    //根据文章id查询评论列表
    @RequestMapping(value = "article/{articleId}", method = RequestMethod.GET)
    public Result findByarticleId(@PathVariable String articleId) {
        List<Comment> list = commentService.findByarticleId(articleId);
        return new Result(true, StatusCode.OK, "查询成功", list);
    }

    //评论点赞
    @RequestMapping(value = "thumbup/{id}", method = RequestMethod.PUT)
    @RequireAuthentication
    public Result thumbup(@PathVariable String id) {
        CommentService.ThumbupResult result = commentService.thumbup(
                id,
                CurrentUserContext.require().getUserId()
        );
        if (result == CommentService.ThumbupResult.DUPLICATE) {
            return new Result(false, StatusCode.REMOTEERROR, "不能重复点赞");
        }
        if (result == CommentService.ThumbupResult.NOT_FOUND) {
            throw new ResourceNotFoundException();
        }
        return new Result(true, StatusCode.OK, "点赞成功");
    }
}
