package com.xspaceagi.system.domain.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xspaceagi.system.domain.service.I18nDomainService;
import com.xspaceagi.system.infra.dao.entity.I18nEntity;
import com.xspaceagi.system.infra.dao.service.I18nService;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.List;

@Service
public class I18nDomainServiceImpl implements I18nDomainService {

    @Resource
    private I18nService i18nService;

    @Override
    public List<I18nEntity> queryI18nEntity(String model, String mid) {
        QueryWrapper<I18nEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("model", model);
        queryWrapper.eq("mid", mid);
        //select * from i18n where model='' and mid = '';
        return i18nService.list(queryWrapper);
    }

    @Override
    public void addI18n(I18nEntity i18nEntity) {
        QueryWrapper<I18nEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("model", i18nEntity.getModel());
        queryWrapper.eq("mid", i18nEntity.getMid());
        queryWrapper.eq("lang", i18nEntity.getLang());
        queryWrapper.eq("field_key", i18nEntity.getFieldKey());
        I18nEntity i18n = i18nService.getOne(queryWrapper);
        if (i18n != null) {
            i18nEntity.setId(i18n.getId());
            i18nService.updateById(i18nEntity);
        } else {
            i18nService.save(i18nEntity);
        }
    }
}
