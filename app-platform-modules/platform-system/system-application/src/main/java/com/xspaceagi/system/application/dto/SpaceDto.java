package com.xspaceagi.system.application.dto;

import com.xspaceagi.system.infra.dao.entity.Space;
import com.xspaceagi.system.infra.dao.entity.SpaceUser;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpaceDto implements Serializable {

    @Schema(description = "空间ID")
    private Long id;

    @Schema(description = "租户ID")
    private Long tenantId;

    @Schema(description = "空间名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "空间名称不能为空")
    private String name;

    @Schema(description = "空间描述")
    private String description;

    @Schema(description = "空间图标")
    private String icon;

    @Schema(description = "空间类型")
    private Space.Type type;

    @Schema(description = "创建者ID")
    private Long creatorId;

    @Schema(description = "创建者名称")
    private String creatorName;

    @Schema(description = "更新时间")
    private Date modified;

    @Schema(description = "创建时间")
    private Date created;

    @Schema(description = "当前登录用户在空间的角色")
    private SpaceUser.Role currentUserRole;

    @Schema(description = "空间是否接收来自外部的发布")
    private Integer receivePublish;

    @Schema(description = "空间是否开启开发功能")
    private Integer allowDevelop;
}
