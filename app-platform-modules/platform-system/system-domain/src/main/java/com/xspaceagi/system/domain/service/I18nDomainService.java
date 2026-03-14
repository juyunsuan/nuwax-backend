package com.xspaceagi.system.domain.service;


import com.xspaceagi.system.infra.dao.entity.I18nEntity;

import java.util.List;

public interface I18nDomainService {

    List<I18nEntity> queryI18nEntity(String model, String mid);

    void addI18n(I18nEntity i18nEntity);
}
