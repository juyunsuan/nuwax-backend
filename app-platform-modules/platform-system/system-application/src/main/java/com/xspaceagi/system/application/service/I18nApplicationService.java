package com.xspaceagi.system.application.service;


import com.xspaceagi.system.application.dto.I18nDto;

import java.util.List;
import java.util.Map;

public interface I18nApplicationService {

    List<I18nDto> queryI18nDtoList(String model, String mid, String field);

    Map<String, String> queryWebSiteI18nKeyValues(String lang);

    void addI18n(I18nDto i18nDto);

    /**
     * 自动国际化替换
     */
    void i18nConvert(Object object);
}
