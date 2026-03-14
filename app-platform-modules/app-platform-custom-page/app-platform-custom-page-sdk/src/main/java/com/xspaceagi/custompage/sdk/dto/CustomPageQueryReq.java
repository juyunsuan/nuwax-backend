package com.xspaceagi.custompage.sdk.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 自定义页面分页查询请求参数
 */
@Data
@Schema(description = "前端项目查询请求体")
public class CustomPageQueryReq {

    @Schema(description = "空间ID")
    private Long spaceId;

    @Schema(description = "发布状态,true:已发布,false:未发布")
    private Boolean buildRunning;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "发布类型")
    private PublishTypeEnum publishType;
}
