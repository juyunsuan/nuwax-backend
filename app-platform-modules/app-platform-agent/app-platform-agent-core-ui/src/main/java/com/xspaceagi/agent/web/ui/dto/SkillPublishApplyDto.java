package com.xspaceagi.agent.web.ui.dto;

import com.xspaceagi.agent.core.adapter.repository.entity.Published;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

@Data
public class SkillPublishApplyDto implements Serializable {

    @Schema(description = "技能ID")
    private Long skillId;

    @Schema(description = "发布范围，Space 空间；Tenant 租户全局；Global 系统全局。目前UI上的\"全局\"指的是租户全局", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "发布范围不能为空")
    private Published.PublishScope scope;

    @Schema(description = "发布记录")
    private String remark;
}
