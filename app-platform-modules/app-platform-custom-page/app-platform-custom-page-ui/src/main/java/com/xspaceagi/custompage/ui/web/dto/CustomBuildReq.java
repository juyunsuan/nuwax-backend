package com.xspaceagi.custompage.ui.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "前端项目构建请求体")
public class CustomBuildReq {

    @NotBlank(message = "projectId 不能为空")
    @Schema(description = "项目ID")
    private Long projectId;

    @NotBlank(message = "publishType 不能为空")
    @Schema(description = "发布类型")
    private String publishType;

}
