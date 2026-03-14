package com.xspaceagi.custompage.ui.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "保活响应体")
public class KeepAliveRes {

    @Schema(description = "项目ID")
    private Long projectId;

    @Schema(description = "项目ID字符串")
    private String projectIdStr;

    @Schema(description = "开发服务器URL")
    private String devServerUrl;

}
