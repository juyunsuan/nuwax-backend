package com.xspaceagi.custompage.ui.web.dto;

import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 会话记录响应DTO
 */
@Data
@Schema(description = "会话记录响应")
public class ConversationRes {

    @Schema(description = "项目ID")
    private Long projectId;

    @Schema(description = "会话ID")
    private Long conversationId;

    @Schema(description = "会话主题")
    private String topic;

    @Schema(description = "会话内容")
    private String content;

    @Schema(description = "创建时间")
    private Date created;

    @Schema(description = "创建者ID")
    private Long creatorId;

}