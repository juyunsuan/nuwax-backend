package com.xspaceagi.custompage.ui.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 删除路径配置请求DTO
 */
@Data
@Schema(description = "删除路径配置请求DTO")
public class DeletePathReq {

    @Schema(description = "项目ID", required = true)
    private Long projectId;

    @Schema(description = "页面路径", required = true, example = "/view")
    private String pageUri;
}
