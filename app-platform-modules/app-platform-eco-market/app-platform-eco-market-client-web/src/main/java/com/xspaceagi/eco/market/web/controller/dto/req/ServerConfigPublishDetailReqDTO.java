package com.xspaceagi.eco.market.web.controller.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "发布配置详情请求DTO")
public class ServerConfigPublishDetailReqDTO {

    @Schema(description = "配置唯一标识")
    @NotBlank(message = "uid不能为空")
    private String uid;

    @Schema(description = "客户端ID")
    @NotBlank(message = "clientId不能为空")
    private String clientId;

    @Schema(description = "客户端密钥")
    @NotBlank(message = "clientSecret不能为空")
    private String clientSecret;
} 