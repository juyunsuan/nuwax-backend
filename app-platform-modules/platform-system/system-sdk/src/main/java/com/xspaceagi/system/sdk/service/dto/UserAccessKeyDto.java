package com.xspaceagi.system.sdk.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserAccessKeyDto {

    private Long id;

    private Long tenantId;

    private Long userId;

    private AKTargetType targetType;

    private String targetId;

    private String accessKey;

    private UserAccessKeyConfig config;

    private Creator creator;

    private Date created;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class UserAccessKeyConfig {

        @Schema(description = "是否开发模式,1 是；0 否")
        private Integer isDevMode;
        private Boolean enabled;
        private Long modelId;
        private String modelBaseUrl;
        private String modelApiKey;
        private String conversationId;
        private String modelName;
        private String protocol;
        private String scope;
        private String userName;// 用户名临时记录
        private String requestId;
    }

    @Data
    public static class Creator {
        private Long userId;
        private String userName;
    }

    public enum AKTargetType {
        Mcp, Agent, Sandbox, TempChat, Tenant, AgentModel
    }
}
