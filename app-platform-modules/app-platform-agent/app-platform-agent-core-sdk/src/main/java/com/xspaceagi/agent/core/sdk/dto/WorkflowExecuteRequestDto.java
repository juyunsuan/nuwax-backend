package com.xspaceagi.agent.core.sdk.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

@Data
public class WorkflowExecuteRequestDto implements Serializable {
    private Object user;
    private String requestId;
    @Schema(description = "会话ID")
    private String conversationId;
    private Long workflowId;
    private Long spaceId;
    private Map<String, Object> params;
    private String config;
    // 用于传递通用智能体的绑定参数配置
    private String bindConfig;
}
