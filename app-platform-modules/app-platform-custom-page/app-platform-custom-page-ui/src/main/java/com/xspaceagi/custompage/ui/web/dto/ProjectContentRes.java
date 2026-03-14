package com.xspaceagi.custompage.ui.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "查询项目文件内容响应")
public class ProjectContentRes {

    @Schema(description = "项目文件内容")
    private Object files;

    @Schema(description = "前端框架")
    private String frontendFramework;

    @Schema(description = "开发框架")
    private String devFramework;
}
