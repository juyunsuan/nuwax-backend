package com.xspaceagi.custompage.sdk.dto;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据源 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataSourceDto implements Serializable {

    @Schema(description = "数据源类型：plugin-插件, workflow-工作流")
    private String type;

    @Schema(description = "数据源ID")
    private Long id;

    @Schema(description = "数据源Key，添加保存的时候可以和ID一致")
    private String key;

    @Schema(description = "数据源名称")
    private String name;

    @Schema(description = "数据源图标")
    private String icon;
}
