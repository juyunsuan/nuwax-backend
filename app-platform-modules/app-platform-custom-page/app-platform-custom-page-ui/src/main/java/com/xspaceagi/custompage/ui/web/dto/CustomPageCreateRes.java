package com.xspaceagi.custompage.ui.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "前端项目创建响应体")
public class CustomPageCreateRes {

    @Schema(description = "项目ID")
    private Long projectId;

    @Schema(description = "开发服务器URL")
    private String devServerUrl;

    @Schema(description = "空间ID")
    private Long spaceId;
}