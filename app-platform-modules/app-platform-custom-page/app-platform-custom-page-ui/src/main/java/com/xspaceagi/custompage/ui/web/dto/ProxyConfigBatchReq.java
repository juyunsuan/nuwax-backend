package com.xspaceagi.custompage.ui.web.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 批量反向代理配置请求DTO
 */
@Data
@Schema(description = "批量反向代理配置请求DTO")
public class ProxyConfigBatchReq {

    @Schema(description = "项目ID", required = true)
    private Long projectId;

    @Schema(description = "反向代理配置列表", required = true)
    private List<ProxyConfigItem> proxyConfigs;

    @Data
    @Schema(description = "反向代理配置项")
    public static class ProxyConfigItem {
        @Schema(description = "环境", required = true, example = "dev")
        private String env;

        @Schema(description = "路径", required = true, example = "/api")
        private String path;

        @Schema(description = "后端地址列表", required = true)
        private List<BackendReq> backends;

        @Schema(description = "健康检查路径", example = "/health")
        private String healthCheckPath;

        @Schema(description = "是否必须登录", example = "true")
        private Boolean requireAuth;
    }

    @Data
    @Schema(description = "后端地址配置")
    public static class BackendReq {
        @Schema(description = "后端地址", required = true)
        private String backend;

        @Schema(description = "权重", example = "1")
        private Integer weight;
    }
}
