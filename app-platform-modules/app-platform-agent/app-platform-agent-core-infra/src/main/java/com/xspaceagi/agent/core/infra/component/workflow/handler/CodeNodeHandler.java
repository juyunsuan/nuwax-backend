package com.xspaceagi.agent.core.infra.component.workflow.handler;

import com.xspaceagi.agent.core.adapter.dto.config.Arg;
import com.xspaceagi.agent.core.adapter.dto.config.workflow.CodeNodeConfigDto;
import com.xspaceagi.agent.core.adapter.dto.config.workflow.WorkflowConfigDto;
import com.xspaceagi.agent.core.adapter.dto.config.workflow.WorkflowNodeDto;
import com.xspaceagi.agent.core.infra.code.CodeArgDto;
import com.xspaceagi.agent.core.infra.code.CodeExecuteResultDto;
import com.xspaceagi.agent.core.infra.code.CodeExecuteService;
import com.xspaceagi.agent.core.infra.component.agent.AgentContext;
import com.xspaceagi.agent.core.infra.component.workflow.WorkflowContext;
import com.xspaceagi.agent.core.spec.enums.CodeLanguageEnum;
import com.xspaceagi.agent.core.spec.enums.DataTypeEnum;
import com.xspaceagi.agent.core.spec.enums.GlobalVariableEnum;
import com.xspaceagi.system.spec.exception.BizException;
import org.apache.commons.collections4.CollectionUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static com.xspaceagi.agent.core.infra.component.ArgExtractUtil.extraParams;
import static com.xspaceagi.agent.core.spec.enums.DataTypeEnum.Array_Object;

public class CodeNodeHandler extends AbstractNodeHandler {

    @Override
    protected Object executeNode(WorkflowContext workflowContext, WorkflowNodeDto node) {
        CodeNodeConfigDto codeNodeConfigDto = (CodeNodeConfigDto) node.getNodeConfig();
        CodeArgDto codeArgDto = new CodeArgDto();
        if (codeNodeConfigDto.getCodeLanguage() == CodeLanguageEnum.Python) {
            codeArgDto.setCode(codeNodeConfigDto.getCodePython());
        }
        if (codeNodeConfigDto.getCodeLanguage() == CodeLanguageEnum.JavaScript) {
            codeArgDto.setCode(codeNodeConfigDto.getCodeJavaScript());
        }

        codeArgDto.setEngineType(codeNodeConfigDto.getCodeLanguage().getName());

        //标识环境隔离用,取工作流的id,因为从上下文中获取可能没有智能体,无法从"workflowContext.getAgentContext().getUserId()" 获取到用户ID
        String envId = Optional.ofNullable(workflowContext.getWorkflowConfig())
                .map(WorkflowConfigDto::getId)
                .map(Object::toString)
                .orElse(String.valueOf(workflowContext.getAgentContext().getUserId()));
        codeArgDto.setUserId(envId);
        // 提取参数
        Map<String, Object> params = extraBindValueMap(workflowContext, node, node.getNodeConfig().getInputArgs());

        AgentContext agentContext = workflowContext.getAgentContext();
        //变量设置
        Map<String, Object> sysVars = new HashMap<>();
        //遍历 GlobalVariableEnum.values()，设置到SYS_VARS里
        if (agentContext != null) {
            for (GlobalVariableEnum globalVariableEnum : GlobalVariableEnum.values()) {
                Object val = agentContext.getVariableParams().get(globalVariableEnum.name());
                if (val != null) {
                    sysVars.put(globalVariableEnum.name(), val);
                }
            }
        }
        sysVars.remove(GlobalVariableEnum.CHAT_CONTEXT.name());
        if (params == null) {
            params = new HashMap<>();
        }
        params.put("SYS_VARS", sysVars);
        codeArgDto.setParams(params);
        CodeExecuteService codeExecuteService = workflowContext.getWorkflowContextServiceHolder().getCodeExecuteService();
        CodeExecuteResultDto codeExecuteResultDto = codeExecuteService.execute(codeArgDto);
        if (Objects.isNull(codeExecuteResultDto.getSuccess()) || !codeExecuteResultDto.getSuccess()) {
            throw new BizException(codeExecuteResultDto.getError());
        }
        Map<String, Object> outputMap = new HashMap<>();
        if (codeExecuteResultDto.getResult() != null && codeExecuteResultDto.getResult() instanceof Map<?, ?>) {
            Map<String, Object> valueMap = (Map<String, Object>) codeExecuteResultDto.getResult();
            for (Arg outputArg : codeNodeConfigDto.getOutputArgs()) {
                if ((outputArg.getDataType() == DataTypeEnum.Object || outputArg.getDataType() == Array_Object) && CollectionUtils.isEmpty(outputArg.getSubArgs())) {
                    outputMap.put(outputArg.getName(), valueMap.get(outputArg.getName()));
                    continue;
                }
                Object value = extraParams(outputArg, valueMap);
                if (value != null) {
                    outputMap.put(outputArg.getName(), value);
                }
            }
        }
        return outputMap;
    }
}
