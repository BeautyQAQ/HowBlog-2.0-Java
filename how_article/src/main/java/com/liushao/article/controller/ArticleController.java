package com.liushao.article.controller;

import com.liushao.article.pojo.Article;
import com.liushao.article.service.ArticleService;
import com.liushao.entity.PageResult;
import com.liushao.entity.Result;
import com.liushao.entity.StatusCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * @author huangshen
 */
@RestController
@RequestMapping("article")
@CrossOrigin
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
        Article article = articleService.findById(articleId);
        return new Result(true, StatusCode.OK, "查询成功", article);
    }

    /**
     * 新增文章数据接口
     */
    @RequestMapping(method = RequestMethod.POST)
    public Result add(@RequestBody Article article) {
        articleService.add(article);
        return new Result(true, StatusCode.OK, "添加成功");
    }

    /**
     * 修改文章数据接口
     */
    @RequestMapping(value = "{articleId}", method = RequestMethod.PUT)
    public Result update(@PathVariable String articleId, @RequestBody Article article) {
        article.setId(articleId);
        articleService.update(article);
        return new Result(true, StatusCode.OK, "修改成功");
    }

    /**
     * 删除文章数据接口
     */
    @RequestMapping(value = "{articleId}", method = RequestMethod.DELETE)
    public Result delete(@PathVariable String articleId) {
        articleService.delete(articleId);
        return new Result(true, StatusCode.OK, "删除成功");
    }

    /**
     * 条件查询
     */
    @RequestMapping(value="search/{page}/{size}", method = RequestMethod.POST)
    public Result search(@RequestBody Map map, @PathVariable int page, @PathVariable int size) {
        Page<Article> pageResult = articleService.search(map, page, size);
        return new Result(true, StatusCode.OK, "查询成功", new PageResult<>(pageResult.getTotalElements(), pageResult.getContent()));
    }
}
