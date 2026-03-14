package com.xspaceagi.system.application.service.impl;

import com.xspaceagi.system.application.dto.I18nDto;
import com.xspaceagi.system.domain.service.I18nDomainService;
import com.xspaceagi.system.application.service.I18nApplicationService;
import com.xspaceagi.system.infra.dao.entity.I18nEntity;
import com.xspaceagi.system.spec.annotation.I18n;
import com.xspaceagi.system.spec.annotation.I18nField;
import com.xspaceagi.system.spec.common.RequestContext;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class I18nApplicationServiceImpl implements I18nApplicationService {

    @Autowired
    private RequestContext loginWrapper;

    @Resource
    private I18nDomainService i18nDomainService;

    @Override
    public List<I18nDto> queryI18nDtoList(String model, String mid, String field) {
        List<I18nEntity> i18nEntities = i18nDomainService.queryI18nEntity(model, mid);
        List<I18nDto> i18nDtos = new ArrayList<>();
        i18nEntities.forEach(i18nEntity -> {
            if (i18nEntity.getFieldKey().equals(field)) {
                I18nDto i18nDto = new I18nDto();
                BeanUtils.copyProperties(i18nEntity, i18nDto);
                i18nDtos.add(i18nDto);
            }
        });

        return i18nDtos;
    }

    @Override
    public Map<String, String> queryWebSiteI18nKeyValues(String lang) {
        List<I18nEntity> i18nEntities = i18nDomainService.queryI18nEntity("web", "-1");
        Map<String, String> allKV = i18nEntities.stream().collect(Collectors.toMap(I18nEntity::getFieldKey, v1 -> v1.getContent(), (v1, v2) -> {
            if (StringUtils.isNotBlank(v1)) {
                return v1;
            }
            return v2;
        }));
        Map<String, String> resKv = new HashMap<>();
        if (lang == null) {
            lang = loginWrapper.getLang();
        }
        String finalLang = lang;
        i18nEntities.forEach(i18nEntity -> {
            if (i18nEntity.getLang().equals(finalLang)) {
                if (StringUtils.isNotBlank(i18nEntity.getContent())) {
                    resKv.put(i18nEntity.getFieldKey(), i18nEntity.getContent());
                } else {
                    resKv.put(i18nEntity.getFieldKey(), allKV.get(i18nEntity.getFieldKey()));
                }
            }
        });
        return resKv;
    }

    @Override
    public void addI18n(I18nDto i18nDto) {
        I18nEntity i18nEntity = new I18nEntity();
        BeanUtils.copyProperties(i18nDto, i18nEntity);
        i18nDomainService.addI18n(i18nEntity);
    }

    @Override
    public void i18nConvert(Object obj) {
        if (obj == null) {
            return;
        }
        if (loginWrapper == null || loginWrapper.getLang() == null) {
            return;
        }
        try {
            if (obj != null) {
                if (obj instanceof List) {
                    if (((List<?>) obj).size() > 0) {
                        for (int i = 0; i < ((List<?>) obj).size(); i++) {
                            convert(((List<?>) obj).get(i));
                        }
                    }
                } else {
                    convert(obj);
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }
    }

    private void convert(Object obj) {
        I18n i18n = obj.getClass().getAnnotation(I18n.class);
        String model = i18n == null ? null : i18n.model();
        String mid = null;
        Class clazz = obj.getClass();
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            String fieldName = field.getName();
            if ("serialVersionUID".equals(fieldName)) continue;
            I18nField i18nField = field.getAnnotation(I18nField.class);
            if (i18nField != null && i18nField.id()) {
                String getter = "get" + fieldName.substring(0, 1).toUpperCase() +
                        (fieldName.length() > 1 ? fieldName.substring(1) : "");
                try {
                    Method method = clazz.getMethod(getter, new Class[]{});
                    method.setAccessible(true);
                    mid = method.invoke(obj, new Object[]{}).toString();
                    break;
                } catch (Exception e) {
                    e.printStackTrace();
                    return;
                }
            }
        }
        Map<String, String> fieldContentMap = new HashMap<>();
        if (model != null && mid != null) {
            List<I18nEntity> i18nEntities = i18nDomainService.queryI18nEntity(model, mid);
            i18nEntities.forEach(i18nEntity -> {
                if (i18nEntity.getLang().equals(loginWrapper.getLang()) && StringUtils.isNotBlank(i18nEntity.getContent())) {
                    fieldContentMap.put(i18nEntity.getFieldKey(), i18nEntity.getContent());
                }
            });
        }

        for (Field field : fields) {
            try {
                String fieldName = field.getName();
                if ("serialVersionUID".equals(fieldName)) continue;
                I18nField i18nField = field.getAnnotation(I18nField.class);
                if (i18nField != null && i18nField.subObj()) {
                    String getter = "get" + fieldName.substring(0, 1).toUpperCase() +
                            (fieldName.length() > 1 ? fieldName.substring(1) : "");
                    Method method = clazz.getMethod(getter, new Class[]{});
                    method.setAccessible(true);
                    Object subObj = method.invoke(obj, new Object[]{});
                    i18nConvert(subObj);
                    continue;
                }

                if (!field.getGenericType().toString().equals("class java.lang.String")) {
                    continue;
                }

                String fieldKey = fieldName;
                if (i18nField != null && !i18nField.id() && StringUtils.isNotBlank(i18nField.field())) {
                    fieldKey = i18nField.field();
                }

                String setter = "set" + fieldName.substring(0, 1).toUpperCase() + (fieldName.length() > 1 ? fieldName.substring(1) : "");
                Method method = clazz.getMethod(setter, String.class);
                method.setAccessible(true);
                String content = fieldContentMap.get(fieldKey);
                if (StringUtils.isNotBlank(content)) {
                    method.invoke(obj, content);
                }
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
        }
    }
}
