package com.liushao.base.service;

import com.liushao.base.dao.LabelDao;
import com.liushao.base.pojo.Label;
import com.liushao.util.IdWorker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LabelService {
    @Autowired
    private LabelDao labelDao;
    @Autowired
    private IdWorker idWorker;

    /**
     * 保存一个标签
     */
    public void saveLabel(Label label){
        //设置ID
        label.setId(idWorker.nextId()+"");
        labelDao.save(label);
    }
    /**
     * 更新一个标签
     */
    public void updateLabel(Label label){
        labelDao.findById(label.getId()).ifPresent(existing -> {
            if (label.getLabelname() != null) existing.setLabelname(label.getLabelname());
            if (label.getState() != null) existing.setState(label.getState());
            if (label.getCount() != null) existing.setCount(label.getCount());
            if (label.getFans() != null) existing.setFans(label.getFans());
            if (label.getRecommend() != null) existing.setRecommend(label.getRecommend());
            labelDao.save(existing);
        });
    }

    /**
     * 删除一个标签
     */
    public void deleteLabelById(String id){
        if (labelDao.existsById(id)) {
            labelDao.deleteById(id);
        }
    }

    /**
     * 查询全部标签
     *
     * @return
     */
    public List<Label> findLabelList() {
        return labelDao.findAll();
    }

    /**
     * 根据ID查询标签
     *
     * @return
     */
    public Label findLabelById(String id) {
        return labelDao.findById(id).orElse(null);
    }

}
