package com.xspaceagi.agent.core.sdk.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

@Data
public class PluginExecuteRequestDto implements Serializable {
    private String requestId;
    private String config;
    // 用于传递通用智能体的绑定参数配置
    private String bindConfig;
    private Long spaceId;
    private Long userId;
    private Object user;
    private Map<String, Object> params;
    private boolean test;
}
