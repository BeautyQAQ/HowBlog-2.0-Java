package com.liushao.article.controller;

import com.liushao.auth.AuthenticatedUser;
import com.liushao.auth.CurrentUserContext;
import com.liushao.auth.RequireAuthentication;
import com.liushao.article.pojo.Article;
import com.liushao.article.service.ArticleService;
import com.liushao.entity.PageResult;
import com.liushao.entity.Result;
import com.liushao.entity.StatusCode;
import com.liushao.web.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.liushao.web.ValidationGroups;

import java.util.List;
import java.util.Map;

/**
 * @author huangshen
 */
@RestController
@RequestMapping("article")
public class ArticleController {

    @Autowired
    private ArticleService articleService;

    @RequestMapping(method = RequestMethod.GET)
    public Result findAll() {
        List list = articleService.findAll();
        return new Result(true, StatusCode.OK, "查询成功", list);
    }

    @RequestMapping(value = "{articleId}", method = RequestMethod.GET)
    public Result findById(@PathVariable String articleId) {
        Article article = articleService.findById(articleId, viewerId());
        return new Result(true, StatusCode.OK, "查询成功", article);
    }

    /**
     * 新增文章数据接口
     */
    @RequestMapping(method = RequestMethod.POST)
    @RequireAuthentication
    public Result add(@Validated(ValidationGroups.Create.class) @RequestBody Article article) {
        articleService.add(article, CurrentUserContext.require().getUserId());
        return new Result(true, StatusCode.OK, "添加成功");
    }

    /**
     * 修改文章数据接口
     */
    @RequestMapping(value = "{articleId}", method = RequestMethod.PUT)
    @RequireAuthentication
    public Result update(@PathVariable String articleId,
                         @Validated(ValidationGroups.Update.class) @RequestBody Article article) {
        article.setId(articleId);
        if (!articleService.update(article, CurrentUserContext.require().getUserId())) {
            return new Result(false, StatusCode.ACCESSERROR, "无权操作或文章不存在");
        }
        return new Result(true, StatusCode.OK, "修改成功");
    }

    /**
     * 删除文章数据接口
     */
    @RequestMapping(value = "{articleId}", method = RequestMethod.DELETE)
    @RequireAuthentication
    public Result delete(@PathVariable String articleId) {
        if (!articleService.delete(articleId, CurrentUserContext.require().getUserId())) {
            return new Result(false, StatusCode.ACCESSERROR, "无权操作或文章不存在");
        }
        return new Result(true, StatusCode.OK, "删除成功");
    }

    /**
     * 发布文章数据接口
     */
    @RequestMapping(value = "{articleId}/publish", method = RequestMethod.PUT)
    @RequireAuthentication
    public Result publish(@PathVariable String articleId) {
        if (!articleService.publish(articleId, CurrentUserContext.require().getUserId())) {
            return new Result(false, StatusCode.ACCESSERROR, "无权操作或文章不存在");
        }
        return new Result(true, StatusCode.OK, "发布成功");
    }

    /**
     * 下架文章数据接口
     */
    @RequestMapping(value = "{articleId}/unpublish", method = RequestMethod.PUT)
    @RequireAuthentication
    public Result unpublish(@PathVariable String articleId) {
        if (!articleService.unpublish(articleId, CurrentUserContext.require().getUserId())) {
            return new Result(false, StatusCode.ACCESSERROR, "无权操作或文章不存在");
        }
        return new Result(true, StatusCode.OK, "已下架");
    }

    /**
     * 提交审核数据接口
     */
    @RequestMapping(value = "{articleId}/submit", method = RequestMethod.PUT)
    @RequireAuthentication
    public Result submit(@PathVariable String articleId) {
        return reviewResult(articleService.submit(articleId, CurrentUserContext.require().getUserId()), "已提交审核");
    }

    /**
     * 审核通过数据接口（仅管理员）
     */
    @RequestMapping(value = "{articleId}/approve", method = RequestMethod.PUT)
    @RequireAuthentication
    public Result approve(@PathVariable String articleId) {
        return reviewResult(articleService.approve(articleId, CurrentUserContext.require().getUserId()), "审核通过");
    }

    /**
     * 审核驳回数据接口（仅管理员）
     */
    @RequestMapping(value = "{articleId}/reject", method = RequestMethod.PUT)
    @RequireAuthentication
    public Result reject(@PathVariable String articleId) {
        return reviewResult(articleService.reject(articleId, CurrentUserContext.require().getUserId()), "已驳回");
    }

    /**
     * 文章点赞数据接口
     */
    @RequestMapping(value = "thumbup/{articleId}", method = RequestMethod.PUT)
    @RequireAuthentication
    public Result thumbup(@PathVariable String articleId) {
        ArticleService.ThumbupResult result = articleService.thumbup(articleId, CurrentUserContext.require().getUserId());
        if (result == ArticleService.ThumbupResult.DUPLICATE) {
            return new Result(false, StatusCode.REMOTEERROR, "不能重复点赞");
        }
        if (result == ArticleService.ThumbupResult.NOT_FOUND) {
            throw new ResourceNotFoundException();
        }
        return new Result(true, StatusCode.OK, "点赞成功");
    }

    private Result reviewResult(ArticleService.TransitionResult result, String successMessage) {
        return switch (result) {
            case SUCCESS -> new Result(true, StatusCode.OK, successMessage);
            case NOT_ALLOWED -> new Result(false, StatusCode.ACCESSERROR, "无权操作或文章不存在");
            case INVALID_STATE -> new Result(false, StatusCode.ERROR, "文章状态不允许该操作");
        };
    }

    /**
     * 条件查询
     */
    @RequestMapping(value="search/{page}/{size}", method = RequestMethod.POST)
    public Result search(@RequestBody Map map, @PathVariable int page, @PathVariable int size) {
        Page<Article> pageResult = articleService.search(map, page, size, viewerId());
        return new Result(true, StatusCode.OK, "查询成功", new PageResult<>(pageResult.getTotalElements(), pageResult.getContent()));
    }

    private String viewerId() {
        return CurrentUserContext.get().map(AuthenticatedUser::getUserId).orElse(null);
    }
}
