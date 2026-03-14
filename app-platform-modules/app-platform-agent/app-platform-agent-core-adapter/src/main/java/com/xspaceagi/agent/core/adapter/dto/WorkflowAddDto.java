package com.xspaceagi.agent.core.adapter.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WorkflowAddDto {

    @Schema(description = "空间ID")
    @NotNull(message = "空间ID不能为空")
    private Long spaceId;

    @Schema(description = "工作流名称")
    @NotNull(message = "工作流名称不能为空")
    private String name;

    @Schema(description = "工作流描述")
    private String description;

    @Schema(description = "图标地址")
    private String icon;

}
