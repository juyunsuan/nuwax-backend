package com.xspaceagi.custompage.ui.web.dto;

import com.xspaceagi.custompage.sdk.dto.SourceTypeEnum;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "前端项目修改请求体")
public class CustomPageUpdateReq {

    @NotNull(message = "projectId 不能为空")
    @Schema(description = "项目ID", required = true)
    private Long projectId;

    @Schema(description = "项目名称", required = true)
    private String projectName;

    @Schema(description = "项目描述")
    private String projectDesc;

    @Schema(description = "项目图标")
    private String icon;

    @Schema(description = "封面图片")
    private String coverImg;

    @Schema(description = "封面图片来源")
    private SourceTypeEnum coverImgSourceType;

    @Schema(description = "是否需要登录,true:需要,false:不需要")
    private Boolean needLogin;

}