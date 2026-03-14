package com.xspaceagi.custompage.sdk.dto;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 项目配置导出DTO
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "项目配置导出DTO")
public class ProjectConfigExportDto implements Serializable {

    @Schema(description = "项目名称")
    private String name;

    @Schema(description = "项目描述")
    private String description;

    @Schema(description = "项目图标")
    private String icon;

    @Schema(description = "封面图片")
    private String coverImg;

    @Schema(description = "封面图片来源")
    private SourceTypeEnum coverImgSourceType;

    @Schema(description = "是否需要登录,true:需要,false:不需要")
    private Boolean needLogin;

    @Schema(description = "代理配置")
    private List<ProxyConfig> proxyConfigs;

    @Schema(description = "页面参数配置")
    private List<PageArgConfig> pageArgConfigs;

    @Schema(description = "绑定的插件")
    private List<Map<String, Object>> dataSourcePlugins;

    @Schema(description = "绑定的工作流")
    private List<Map<String, Object>> dataSourceWorkflows;
}