package com.xspaceagi.eco.market.domain.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "配置审批请求DTO")
public class ServerConfigApproveReqDTO {

    @Schema(description = "配置UID")
    @NotBlank(message = "配置UID不能为空")
    private String uid;

    @Schema(description = "是否批准")
    @NotNull(message = "批准状态不能为空")
    private Boolean approved;


    

    // @Schema(description = "客户端ID")
    // @NotBlank(message = "clientId不能为空")
    // private String clientId;

    // @Schema(description = "客户端密钥")
    // @NotBlank(message = "clientSecret不能为空")
    // private String clientSecret;
} 