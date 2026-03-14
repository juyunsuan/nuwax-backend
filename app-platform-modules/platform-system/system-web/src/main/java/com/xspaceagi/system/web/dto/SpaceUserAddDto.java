package com.xspaceagi.system.web.dto;

import com.xspaceagi.system.infra.dao.entity.SpaceUser;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

@Data
public class SpaceUserAddDto implements Serializable {

    @Schema(description = "空间ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "空间ID不能为空")
    private Long spaceId;

    @Schema(description = "用户ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @Schema(description = "用户角色", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "用户角色不能为空")
    private SpaceUser.Role role;
}
