package com.xspaceagi.agent.core.infra.component.workflow.handler;

import com.xspaceagi.agent.core.adapter.dto.AttachmentDto;
import com.xspaceagi.agent.core.adapter.dto.KnowledgeSearchConfigDto;
import com.xspaceagi.agent.core.adapter.dto.config.Arg;
import com.xspaceagi.agent.core.adapter.dto.config.plugin.PluginDto;
import com.xspaceagi.agent.core.adapter.dto.config.workflow.LLMNodeConfigDto;
import com.xspaceagi.agent.core.adapter.dto.config.workflow.WorkflowConfigDto;
import com.xspaceagi.agent.core.adapter.dto.config.workflow.WorkflowNodeDto;
import com.xspaceagi.agent.core.infra.component.agent.AgentContext;
import com.xspaceagi.agent.core.infra.component.model.ModelContext;
import com.xspaceagi.agent.core.infra.component.model.ModelInvoker;
import com.xspaceagi.agent.core.infra.component.model.dto.ComponentConfig;
import com.xspaceagi.agent.core.infra.component.model.dto.ModelCallConfigDto;
import com.xspaceagi.agent.core.infra.component.workflow.WorkflowContext;
import com.xspaceagi.agent.core.spec.enums.ComponentTypeEnum;
import com.xspaceagi.agent.core.spec.enums.OutputTypeEnum;
import com.xspaceagi.agent.core.spec.utils.PlaceholderParser;
import com.xspaceagi.mcp.sdk.dto.McpDto;
import com.xspaceagi.mcp.sdk.dto.McpToolDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class LLMNodeHandler extends AbstractNodeHandler {

    @Override
    public Mono<Object> execute(WorkflowContext workflowContext, WorkflowNodeDto node) {
        log.debug("execute LLMNodeHandler,node {}", node);
        LLMNodeConfigDto llmNodeConfigDto = (LLMNodeConfigDto) node.getNodeConfig();
        Map<String, Object> valueMap = extraBindValueMap(workflowContext, node, llmNodeConfigDto.getInputArgs());
        String systemPrompt = PlaceholderParser.resoleAndReplacePlaceholder(valueMap, llmNodeConfigDto.getSystemPrompt());
        String userPrompt = PlaceholderParser.resoleAndReplacePlaceholder(valueMap, llmNodeConfigDto.getUserPrompt());
        ModelContext modelContext = new ModelContext();
        AgentContext agentContext = workflowContext.getCopiedAgentContext();
        //从userPrompt中提取图片链接
        agentContext.setAttachments(extractAttachments(userPrompt));
        modelContext.setAgentContext(agentContext);
        modelContext.setModelConfig(llmNodeConfigDto.getModelConfig());
        modelContext.setConversationId(workflowContext.getAgentContext().getConversationId());
        modelContext.setRequestId(workflowContext.getRequestId());
        modelContext.setNodeExecutingConsumer(workflowContext.getNodeExecutingConsumer());
        ModelCallConfigDto modelCallConfigDto = new ModelCallConfigDto();
        modelCallConfigDto.setSystemPrompt(systemPrompt);
        modelCallConfigDto.setUserPrompt(userPrompt);
        modelCallConfigDto.setChatRound(0);
        modelCallConfigDto.setStreamCall(true);
        modelCallConfigDto.setOutputType(llmNodeConfigDto.getOutputType());
        if (!CollectionUtils.isEmpty(llmNodeConfigDto.getOutputArgs())) {
            modelCallConfigDto.setOutputArgs(llmNodeConfigDto.getOutputArgs().stream().map((arg) -> {
                arg.setRequire(true);
                return arg;
            }).toList());
        }
        modelCallConfigDto.setMaxTokens(llmNodeConfigDto.getMaxTokens());
        modelCallConfigDto.setTemperature(llmNodeConfigDto.getTemperature());
        modelCallConfigDto.setTopP(llmNodeConfigDto.getTopP());
        modelContext.setModelCallConfig(modelCallConfigDto);
        List<ComponentConfig> componentConfigs = convertToComponentConfig(llmNodeConfigDto.getSkillComponentConfigs(), valueMap);
        modelCallConfigDto.setComponentConfigs(componentConfigs);
        ModelInvoker modelInvoker = workflowContext.getWorkflowContextServiceHolder().getModelInvoker();
        return modelInvoker.invoke(modelContext).last().map(result -> {
            if (llmNodeConfigDto.getOutputType() == OutputTypeEnum.Text || llmNodeConfigDto.getOutputType() == OutputTypeEnum.Markdown) {
                if (!CollectionUtils.isEmpty(llmNodeConfigDto.getOutputArgs())) {
                    Map<String, Object> outputMap = new HashMap<>();
                    outputMap.put(llmNodeConfigDto.getOutputArgs().get(0).getName(), modelContext.getModelCallResult().getResponseText());
                    return outputMap;
                } else {
                    return modelContext.getModelCallResult().getResponseText();
                }
            } else {
                return modelContext.getModelCallResult().getData();
            }
        });
    }

    private List<ComponentConfig> convertToComponentConfig(List<LLMNodeConfigDto.SkillComponentConfigDto> skillComponentConfigs, Map<String, Object> params) {
        if (skillComponentConfigs == null) {
            return List.of();
        }
        List<ComponentConfig> componentConfigs = new ArrayList<>();
        for (LLMNodeConfigDto.SkillComponentConfigDto skillComponentConfig : skillComponentConfigs) {
            ComponentConfig componentConfig = new ComponentConfig();
            componentConfig.setName(skillComponentConfig.getName());
            componentConfig.setIcon(skillComponentConfig.getIcon());
            componentConfig.setTargetId(skillComponentConfig.getTypeId());
            componentConfig.setDescription(skillComponentConfig.getDescription());
            componentConfig.setParams(params);
            List<Arg> inputArgBindConfigs = skillComponentConfig.getInputArgBindConfigs();
            if (!CollectionUtils.isEmpty(inputArgBindConfigs)) {
                componentConfig.setInputArgs(inputArgBindConfigs);
            }

            switch (skillComponentConfig.getType()) {
                case Knowledge:
                    componentConfig.setType(ComponentTypeEnum.Knowledge);
                    KnowledgeSearchConfigDto knowledgeSearchConfigDto = new KnowledgeSearchConfigDto();
                    knowledgeSearchConfigDto.setSearchStrategy(skillComponentConfig.getKbRecallStrategy());
                    knowledgeSearchConfigDto.setMatchingDegree(skillComponentConfig.getKbMinMatchScore());
                    knowledgeSearchConfigDto.setMaxRecallCount(skillComponentConfig.getKbMaxRecallCount());
                    componentConfig.setTargetConfig(knowledgeSearchConfigDto);
                    break;
                case Plugin:
                    componentConfig.setType(ComponentTypeEnum.Plugin);
                    PluginDto pluginDto = (PluginDto) skillComponentConfig.getTargetConfig();
                    componentConfig.setTargetConfig(pluginDto);
                    componentConfig.setName(pluginDto.getName());
                    componentConfig.setFunctionName(pluginDto.getFunctionName());
                    componentConfig.setDescription(pluginDto.getDescription());
                    break;
                case Workflow:
                    componentConfig.setType(ComponentTypeEnum.Workflow);
                    WorkflowConfigDto workflowConfigDto = (WorkflowConfigDto) skillComponentConfig.getTargetConfig();
                    componentConfig.setTargetConfig(workflowConfigDto);
                    componentConfig.setName(workflowConfigDto.getName());
                    componentConfig.setFunctionName(workflowConfigDto.getFunctionName());
                    componentConfig.setDescription(workflowConfigDto.getDescription());
                    componentConfig.setOriginalTargetId(workflowConfigDto.getId());
                    break;
                case Mcp:
                    componentConfig.setType(ComponentTypeEnum.Mcp);
                    McpDto mcpDto = (McpDto) skillComponentConfig.getTargetConfig();
                    componentConfig.setTargetConfig(mcpDto);
                    componentConfig.setName(mcpDto.getName());
                    componentConfig.setFunctionName(skillComponentConfig.getToolName());
                    String toolDescription = getMcpToolDescription(mcpDto, skillComponentConfig.getToolName());
                    componentConfig.setDescription(toolDescription);
                    break;
                default:
                    break;
            }
            componentConfigs.add(componentConfig);
        }
        return componentConfigs;
    }

    private static String getMcpToolDescription(McpDto targetConfig, String toolName) {
        if (targetConfig.getDeployedConfig().getTools() == null) {
            return targetConfig.getDescription();
        }
        for (McpToolDto tool : targetConfig.getDeployedConfig().getTools()) {
            if (tool.getName().equals(toolName)) {
                return tool.getDescription();
            }
        }
        return targetConfig.getDescription();
    }

    private static List<AttachmentDto> extractAttachments(String text) {
        List<AttachmentDto> attachmentDtos = new ArrayList<>();
        try {
            // 正则表达式匹配常见图片格式的URL
            String regex = "((https?://[^\\s\"']+?\\.(jpg|jpeg|png|gif|bmp|webp))(\\?\\S+)?)";
            Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(text);

            while (matcher.find()) {
                AttachmentDto attachmentDto = new AttachmentDto();
                attachmentDto.setFileUrl(matcher.group(1));
                attachmentDto.setMimeType("image/jpeg");
                attachmentDtos.add(attachmentDto);
            }
        } catch (Exception e) {
            // 处理异常
            log.warn("Error extracting image URLs: {}", e.getMessage());
        }
        return attachmentDtos;
    }
}
