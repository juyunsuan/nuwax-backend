package com.xspaceagi.custompage.ui.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "域名绑定更新请求体")
public class CustomPageDomainUpdateReq {

    @NotNull(message = "id 不能为空")
    @Schema(description = "域名绑定ID", required = true)
    private Long id;

    @NotBlank(message = "domain 不能为空")
    @Schema(description = "域名", required = true)
    private String domain;

}
