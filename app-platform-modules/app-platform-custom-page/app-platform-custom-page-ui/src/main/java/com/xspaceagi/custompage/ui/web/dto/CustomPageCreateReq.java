package com.xspaceagi.custompage.ui.web.dto;

import org.springframework.web.multipart.MultipartFile;

import com.xspaceagi.custompage.sdk.dto.SourceTypeEnum;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "前端项目创建请求体")
public class CustomPageCreateReq {

    @Schema(description = "项目ID，如果提供则更新现有项目")
    private Long projectId;

    @NotBlank(message = "projectName 不能为空")
    @Schema(description = "项目名称")
    private String projectName;

    @Schema(description = "项目描述")
    private String projectDesc;

    @Schema(description = "文件压缩包")
    private MultipartFile file;

    @Schema(description = "空间ID")
    private Long spaceId;

    @Schema(description = "项目图标")
    private String icon;

    @Schema(description = "封面图片")
    private String coverImg;

    @Schema(description = "封面图片来源")
    private SourceTypeEnum coverImgSourceType;

}