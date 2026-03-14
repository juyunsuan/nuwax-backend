package com.xspaceagi.agent.core.adapter.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgentAddDto implements Serializable {

    @Schema(description = "空间ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "空间ID不能为空")
    private Long spaceId; // 空间ID

    @Schema(description = "Agent名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Agent名称不能为空")
    private String name; // Agent名称

    @Schema(description = "Agent描述")
    private String description; // Agent描述

    @Schema(description = "图标地址")
    private String icon; // 图标地址

    @Schema(description = "类型，ChatBot 对话智能体；TaskAgent 任务型智能体", requiredMode = Schema.RequiredMode.REQUIRED)
    private String type;
}
