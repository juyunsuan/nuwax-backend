package com.xspaceagi.custompage.ui.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "域名绑定删除请求体")
public class CustomPageDomainDeleteReq {

    @NotNull(message = "id 不能为空")
    @Schema(description = "域名绑定ID", required = true)
    private Long id;

}
