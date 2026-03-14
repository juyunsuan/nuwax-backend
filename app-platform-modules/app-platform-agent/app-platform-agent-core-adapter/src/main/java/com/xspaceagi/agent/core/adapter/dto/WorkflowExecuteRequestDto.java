package com.xspaceagi.agent.core.adapter.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

@Data
public class WorkflowExecuteRequestDto implements Serializable {

    @Schema(description = "请求ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "请求ID不能为空")
    private String requestId;

    @Schema(description = "工作流ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "工作流ID不能为空")
    private Long workflowId;

    @Schema(description = "工作流参数", requiredMode = Schema.RequiredMode.REQUIRED)
    private Map<String, Object> params;

    @Schema(description = "问答回复")
    private String answer;

    @Schema(description = "AgentID")
    private Long agentId;

    @Schema(description = "来源")
    private String from;

    public Map<String, Object> getParams() {
        return params == null ? params = new HashMap<>() : params;
    }
}
