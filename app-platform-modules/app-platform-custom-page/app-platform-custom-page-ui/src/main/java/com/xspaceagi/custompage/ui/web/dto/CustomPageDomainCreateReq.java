package com.xspaceagi.custompage.ui.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "域名绑定创建请求体")
public class CustomPageDomainCreateReq {

    @NotNull(message = "projectId 不能为空")
    @Schema(description = "项目ID", required = true)
    private Long projectId;

    @NotBlank(message = "domain 不能为空")
    @Schema(description = "域名", required = true)
    private String domain;

}
