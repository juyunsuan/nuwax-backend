package com.xspaceagi.custompage.domain.model;

import java.util.Date;

import lombok.Data;

@Data
public class CustomPageConversationModel {

    private Long id;

    // 项目ID
    private Long projectId;

    // 会话主题
    private String topic;

    // 会话内容
    private String content;

    private Long tenantId;

    private Long spaceId;

    private Date created;
    private Long creatorId;
    private String creatorName;
    private Date modified;
    private Long modifiedId;
    private String modifiedName;

    private Integer yn;
}
