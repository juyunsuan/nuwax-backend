package com.xspaceagi.agent.core.infra.component.workflow.handler;

import com.xspaceagi.agent.core.adapter.dto.config.workflow.WorkflowNodeDto;
import com.xspaceagi.agent.core.infra.component.workflow.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
public class EmptyExecuteHandler implements NodeHandler {

    @Override
    public Mono<Object> execute(WorkflowContext workflowContext, WorkflowNodeDto node) {
        log.info("节点执行，ID {}, 名称 {}", node.getId(), node.getName());
        return Mono.just(Map.of());
    }
}
