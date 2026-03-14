package com.xspaceagi.custompage.ui.web.dto;

import com.xspaceagi.custompage.sdk.dto.CopyTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 复制项目请求
 */
@Data
public class CustomPageCopyReq {

    @Schema(description = "源项目ID", required = true)
    private Long projectId;

    @Schema(description = "目标空间ID", required = true)
    private Long targetSpaceId;

    @Schema(description = "目标空间类型", required = true)
    private CopyTypeEnum copyType;

}