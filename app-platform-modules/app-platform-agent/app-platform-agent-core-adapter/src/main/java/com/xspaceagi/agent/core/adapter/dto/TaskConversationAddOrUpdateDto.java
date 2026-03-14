package com.xspaceagi.agent.core.adapter.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TaskConversationAddOrUpdateDto {

    @Schema(description = "会话ID")
    private Long id;

    @Schema(description = "任务会话主题")
    @NotNull(message = "任务会话主题不能为空")
    private String topic;

    @Schema(description = "任务会话内容")
    @NotNull(message = "任务会话内容不能为空")
    private String summary;

    @Schema(description = "任务会话定时配置")
    @NotNull(message = "任务会话定时配置不能为空")
    private String taskCron;

    @Schema(description = "智能体ID")
    @NotNull(message = "智能体ID不能为空")
    private Long agentId;

    @Schema(description = "开发模式")
    private boolean devMode;
}
