package com.xspaceagi.custompage.ui.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "绑定开发智能体请求体")
public class BindDevAgentReq {

    @NotNull(message = "projectId 不能为空")
    @Schema(description = "项目ID")
    private Long projectId;

    @NotNull(message = "devAgentId 不能为空")
    @Schema(description = "开发智能体ID")
    private Long devAgentId;

}
