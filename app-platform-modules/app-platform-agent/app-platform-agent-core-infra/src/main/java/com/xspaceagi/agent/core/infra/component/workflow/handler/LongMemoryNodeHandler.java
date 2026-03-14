package com.xspaceagi.agent.core.infra.component.workflow.handler;

import com.xspaceagi.agent.core.adapter.dto.config.workflow.WorkflowNodeDto;
import com.xspaceagi.agent.core.infra.component.workflow.WorkflowContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LongMemoryNodeHandler extends AbstractNodeHandler {

    @Override
    protected Object executeNode(WorkflowContext workflowContext, WorkflowNodeDto node) {
        Map<String, Object> output = new HashMap<>();
        output.put("outputList", List.of(workflowContext.getAgentContext().getLongMemory()));
        return output;
    }
}
