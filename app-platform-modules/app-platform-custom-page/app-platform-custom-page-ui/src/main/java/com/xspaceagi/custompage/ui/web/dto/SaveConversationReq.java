package com.xspaceagi.custompage.ui.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "保存会话记录请求")
public class SaveConversationReq {

    @Schema(description = "项目ID", required = true)
    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    @Schema(description = "会话主题")
    @NotBlank(message = "会话内容不能为空")
    private String topic;

    @Schema(description = "会话内容", required = true)
    @NotBlank(message = "会话内容不能为空")
    private String content;

    @Schema(description = "会话摘要")
    private String summary;
}
