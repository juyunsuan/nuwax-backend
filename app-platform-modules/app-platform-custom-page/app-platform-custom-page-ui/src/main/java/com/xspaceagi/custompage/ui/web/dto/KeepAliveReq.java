package com.xspaceagi.custompage.ui.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "保活请求体")
public class KeepAliveReq {

    @NotBlank(message = "projectId 不能为空")
    @Schema(description = "项目ID")
    private Long projectId;

}
