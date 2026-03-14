package com.xspaceagi.custompage.ui.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "前端项目删除请求体")
public class CustomPageDeleteReq {

    @NotNull(message = "projectId 不能为空")
    @Schema(description = "项目ID", required = true)
    private Long projectId;

}
