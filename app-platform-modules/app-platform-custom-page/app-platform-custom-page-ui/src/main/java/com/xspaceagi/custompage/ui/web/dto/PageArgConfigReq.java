package com.xspaceagi.custompage.ui.web.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 页面参数配置请求DTO
 */
@Data
@Schema(description = "页面参数配置请求DTO")
public class PageArgConfigReq {

    @Schema(description = "项目ID", required = true)
    private Long projectId;

    @Schema(description = "页面路径", required = true, example = "/view")
    private String pageUri;

    @Schema(description = "页面名称", example = "查看页面")
    private String name;

    @Schema(description = "页面描述", example = "用于查看数据的页面")
    private String description;

    @Schema(description = "参数配置列表", required = true)
    private List<PageArgReq> args;

    @Data
    @Schema(description = "页面参数配置")
    public static class PageArgReq {
        @Schema(description = "参数key，唯一标识")
        private String key;

        @Schema(description = "参数名称，符合函数命名规则", required = true, example = "userId")
        private String name;

        @Schema(description = "参数详细描述信息", example = "用户ID")
        private String description;

        @Schema(description = "数据类型", required = true, example = "String")
        private String dataType;

        @Schema(description = "是否必须", example = "true")
        private Boolean require;

        @Schema(description = "是否开启（对模型可见，默认开启）", example = "true")
        private Boolean enable;

        @Schema(description = "默认值", example = "123")
        private String bindValue;

        @Schema(description = "输入类型", example = "text")
        private String inputType;
    }
}
