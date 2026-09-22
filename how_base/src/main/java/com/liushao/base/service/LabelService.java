package com.liushao.base.service;

import com.liushao.base.dao.LabelDao;
import com.liushao.base.dao.UserRoleDao;
import com.liushao.auth.CurrentUserContext;
import com.liushao.base.pojo.Label;
import com.liushao.util.IdWorker;
import com.liushao.web.ResourceNotFoundException;
import com.liushao.web.ForbiddenException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LabelService {
    private final LabelDao labelDao;
    private final IdWorker idWorker;
    private final UserRoleDao userRoleDao;

    public LabelService(LabelDao labelDao, IdWorker idWorker, UserRoleDao userRoleDao) {
        this.labelDao = labelDao;
        this.idWorker = idWorker;
        this.userRoleDao = userRoleDao;
    }

    private void requireAdministrator() {
        String userId = CurrentUserContext.get().orElseThrow(ForbiddenException::new).getUserId();
        if (userRoleDao.countAdministrator(userId) == 0) {
            throw new ForbiddenException();
        }
    }

    /**
     * 保存一个标签
     */
    public void saveLabel(Label label){
        requireAdministrator();
        //设置ID
        label.setId(idWorker.nextId()+"");
        labelDao.save(label);
    }
    /**
     * 更新一个标签
     */
    public void updateLabel(Label label){
        requireAdministrator();
        Label existing = labelDao.findById(label.getId()).orElseThrow(ResourceNotFoundException::new);
            if (label.getLabelname() != null) existing.setLabelname(label.getLabelname());
            if (label.getState() != null) existing.setState(label.getState());
            if (label.getCount() != null) existing.setCount(label.getCount());
            if (label.getFans() != null) existing.setFans(label.getFans());
            if (label.getRecommend() != null) existing.setRecommend(label.getRecommend());
            labelDao.save(existing);
    }

    /**
     * 删除一个标签
     */
    public void deleteLabelById(String id){
        requireAdministrator();
        Label existing = labelDao.findById(id).orElseThrow(ResourceNotFoundException::new);
        labelDao.delete(existing);
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
        return labelDao.findById(id).orElseThrow(ResourceNotFoundException::new);
    }

}
