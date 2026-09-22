package com.liushao.base.controller;

import com.liushao.auth.RequireAuthentication;
import com.liushao.base.pojo.Label;
import com.liushao.base.service.LabelService;
import com.liushao.entity.Result;
import com.liushao.entity.StatusCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 标签控制层
 * @author huangshen
 */
@RestController
@RequestMapping("/label")
@CrossOrigin
public class LabelController {

    @Autowired
    private LabelService labelService;

    /**
     * 添加一个
     */
    @PostMapping
    @RequireAuthentication
    public Result add(@RequestBody Label label) {
        labelService.saveLabel(label);
        return new Result(true, StatusCode.OK, "添加成功");
    }

    /**
     * 修改编辑
     */
    @PutMapping("/{id}")
    @RequireAuthentication
    public Result edit(@RequestBody Label label, @PathVariable String id) {
        label.setId(id);
        labelService.updateLabel(label);
        return new Result(true, StatusCode.OK, "修改成功");
    }

    /**
     * 根据id删除一个
     */
    @DeleteMapping("/{id}")
    @RequireAuthentication
    public Result remove(@PathVariable String id) {
        labelService.deleteLabelById(id);
        return new Result(true, StatusCode.OK, "删除成功");
    }


    /**
     * 查询所有
     */
    @GetMapping
    public Result list() {
        List<Label> list = labelService.findLabelList();
        return new Result(true, StatusCode.OK, "查询成功", list);
    }

    /**
     * 根据id查询一个
     */
    @GetMapping("/{id}")
    public Result listById(@PathVariable String id) {
        Label label = labelService.findLabelById(id);
        return new Result(true, StatusCode.OK, "查询成功", label);
    }
}
