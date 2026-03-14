package com.xspaceagi.eco.market.domain.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "客户端验证请求DTO")
public class ClientValidateReqDTO {

    @Schema(description = "客户端ID")
    @NotBlank(message = "clientId不能为空")
    private String clientId;

    @Schema(description = "客户端密钥")
    @NotBlank(message = "clientSecret不能为空")
    private String clientSecret;
} 