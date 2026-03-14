package com.xspaceagi.custompage.ui.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 保存数据源请求
 */
@Data
public class BindDataSourceReq {

    @Schema(description = "项目ID", required = true)
    private Long projectId;

    @Schema(description = "数据源类型：plugin-插件, workflow-工作流", required = true)
    private String type;

    @Schema(description = "数据源ID", required = true)
    private Long dataSourceId;
}
