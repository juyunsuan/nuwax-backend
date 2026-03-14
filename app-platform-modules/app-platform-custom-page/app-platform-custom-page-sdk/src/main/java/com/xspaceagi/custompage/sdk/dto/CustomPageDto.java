package com.xspaceagi.custompage.sdk.dto;

import java.util.Date;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 用户页面信息DTO
 */
@Data
@Schema(description = "用户页面信息DTO")
public class CustomPageDto {

    @Schema(description = "项目ID")
    private Long projectId;

    @Schema(description = "项目ID字符串")
    private String projectIdStr;

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

    @Schema(description = "项目基础路径")
    private String basePath;

    @Schema(description = "发布状态,true:已发布,false:未发布")
    private Boolean buildRunning;

    @Schema(description = "发布时间")
    private Date buildTime;

    @Schema(description = "发布版本")
    private Integer buildVersion;

    @Schema(description = "发布类型")
    private PublishTypeEnum publishType;

    @Schema(description = "代码版本")
    private Integer codeVersion;

    @Schema(description = "版本信息")
    private Object versionInfo;

    @Schema(description = "上次对话模型ID")
    private Long lastChatModelId;

    @Schema(description = "上次多模态ID")
    private Long lastMultiModelId;

    @Schema(description = "是否需要登录,true:需要,false:不需要")
    private Boolean needLogin;

    @Schema(description = "调试关联智能体")
    private Long devAgentId;

    @Schema(description = "项目类型")
    private ProjectType projectType;

    @Schema(description = "代理配置")
    private List<ProxyConfig> proxyConfigs;

    @Schema(description = "页面参数配置")
    private List<PageArgConfig> pageArgConfigs;

    @Schema(description = "绑定的数据源")
    private List<DataSourceDto> dataSources;

    @Schema(description = "扩展字段")
    private Object ext;

    @Schema(description = "租户ID")
    private Long tenantId;

    @Schema(description = "空间ID")
    private Long spaceId;

    @Schema(description = "创建时间")
    private Date created;

    @Schema(description = "创建者ID")
    private Long creatorId;

    @Schema(description = "创建者名称")
    private String creatorName;

    @Schema(description = "创建者昵称")
    private String creatorNickName;

    @Schema(description = "创建者头像")
    private String creatorAvatar;

    @Schema(description = "页面URL")
    private String pageUrl;
}