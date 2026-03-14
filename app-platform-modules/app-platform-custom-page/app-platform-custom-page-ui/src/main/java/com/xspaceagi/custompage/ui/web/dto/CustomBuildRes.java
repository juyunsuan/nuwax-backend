package com.xspaceagi.custompage.ui.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "前端项目构建响应体")
public class CustomBuildRes {

    @Schema(description = "项目ID")
    private Long projectId;

    @Schema(description = "项目ID字符串")
    private String projectIdStr;

    @Schema(description = "开发服务器URL")
    private String devServerUrl;

    @Schema(description = "线上服务器URL")
    private String prodServerUrl;

    // @Schema(description = "智能体ID")
    // private Long agentId;
}
