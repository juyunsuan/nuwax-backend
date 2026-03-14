package com.xspaceagi.system.infra.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("content_i18n")
public class I18nEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String model;

    private String mid;

    private String lang;

    private String fieldKey;

    private String content;
}
