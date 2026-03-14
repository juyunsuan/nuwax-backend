package com.xspaceagi.sandbox.sdk.service.dto;

import com.xspaceagi.sandbox.spec.enums.SandboxScopeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 沙盒配置 RPC DTO
 */
@Schema(description = "沙盒配置信息（RPC）")
@Data
public class SandboxConfigRpcDto {

    @Schema(description = "配置ID")
    private Long id;

    @Schema(description = "配置范围：global-全局配置 user-个人配置")
    private SandboxScopeEnum scope;

    @Schema(description = "用户ID（scope为user时必填）")
    private Long userId;

    @Schema(description = "配置名称")
    private String name;

    @Schema(description = "唯一标识")
    private String configKey;

    @Schema(description = "配置值")
    private SandboxConfigValue configValue;

    @Schema(description = "沙盒服务器信息")
    private SandboxServerInfo sandboxServerInfo;

    @Schema(description = "配置描述")
    private String description;

    @Schema(description = "是否启用：true-启用 false-禁用")
    private Boolean isActive;

    @Schema(description = "是否在线：true-在线 false-离线")
    private boolean online;

    @Schema(description = "创建时间")
    private Date created;

    @Schema(description = "更新时间")
    private Date modified;
}
