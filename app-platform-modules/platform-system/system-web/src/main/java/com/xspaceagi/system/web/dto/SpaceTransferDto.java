package com.xspaceagi.system.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

@Data
public class SpaceTransferDto implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "空间ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "空间ID不能为空")
    private Long spaceId;

    @Schema(description = "目标用户ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "目标用户ID不能为空")
    private Long targetUserId;
}
