package com.xspaceagi.custompage.ui.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 会话记录分页查询请求参数
 */
@Data
@Schema(description = "会话记录分页查询请求参数")
public class ConversationPageQueryReq {

    @Schema(description = "项目ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long projectId;

}