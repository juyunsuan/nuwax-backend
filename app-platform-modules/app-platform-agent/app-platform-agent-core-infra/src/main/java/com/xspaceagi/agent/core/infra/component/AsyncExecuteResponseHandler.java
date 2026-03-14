package com.xspaceagi.agent.core.infra.component;

import com.alibaba.fastjson2.JSON;
import com.xspaceagi.agent.core.adapter.application.ConversationApplicationService;
import com.xspaceagi.agent.core.adapter.dto.ChatMessageDto;
import com.xspaceagi.agent.core.adapter.dto.config.ModelConfigDto;
import com.xspaceagi.agent.core.adapter.dto.config.bind.ModelBindConfigDto;
import com.xspaceagi.agent.core.adapter.dto.config.workflow.EndNodeConfigDto;
import com.xspaceagi.agent.core.adapter.dto.config.workflow.WorkflowConfigDto;
import com.xspaceagi.agent.core.infra.component.agent.AgentContext;
import com.xspaceagi.agent.core.infra.component.model.ModelContext;
import com.xspaceagi.agent.core.infra.component.model.ModelInvoker;
import com.xspaceagi.agent.core.infra.component.model.dto.ModelCallConfigDto;
import com.xspaceagi.agent.core.infra.component.plugin.PluginContext;
import com.xspaceagi.agent.core.infra.component.workflow.WorkflowContext;
import com.xspaceagi.agent.core.spec.enums.ComponentTypeEnum;
import com.xspaceagi.agent.core.spec.enums.MessageTypeEnum;
import com.xspaceagi.agent.core.spec.utils.PlaceholderParser;
import com.xspaceagi.system.application.dto.EventDto;
import com.xspaceagi.system.application.service.NotifyMessageApplicationService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class AsyncExecuteResponseHandler {

    private NotifyMessageApplicationService notifyMessageApplicationService;

    // 当前上下文记忆
    private ChatMemory chatMemory;

    private ModelInvoker modelInvoker;

    @Autowired
    public void setNotifyMessageApplicationService(NotifyMessageApplicationService notifyMessageApplicationService) {
        this.notifyMessageApplicationService = notifyMessageApplicationService;
    }

    @Autowired
    public void setChatMemory(ConversationApplicationService conversationApplicationService) {
        this.chatMemory = (ChatMemory) conversationApplicationService;
    }

    @Autowired
    public void setModelInvoker(ModelInvoker modelInvoker) {
        this.modelInvoker = modelInvoker;
    }

    public void handleWorkflowSuccess(WorkflowContext workflowContext, Object result) {
        AgentContext agentContext = workflowContext.getCopiedAgentContext();
        if (agentContext == null) {
            return;
        }
        WorkflowConfigDto workflowConfigDto = workflowContext.getWorkflowConfig();
        EndNodeConfigDto endNodeConfigDto = (EndNodeConfigDto) workflowConfigDto.getEndNode().getNodeConfig();
        if (endNodeConfigDto.getReturnType() == EndNodeConfigDto.ReturnType.TEXT && StringUtils.isNotBlank(workflowContext.getEndNodeContent())) {
            addMessage(agentContext, workflowContext.getEndNodeContent());
        } else {
            buildAndAddMessage(agentContext, result);
        }
    }

    private void buildAndAddMessage(AgentContext agentContext, Object result) {
        String userPrompt = "<tool_call_result>" + PlaceholderParser.parseString(result) + "</tool_call_result>\n" +
                "<user_message>" + agentContext.getMessage() + "</user_message>\n";
        String systemPrompt = """
                # Role：
                 - 信息整合与优化输出专家
                
                ## Background：
                - 用户需要将工具执行结果转化为可读性高的回答，同时避免输出原始标签和冗余信息
                
                ## Attention：
                - 保持回答的专业性和准确性，同时提升信息的可读性和用户体验
                
                ## Profile：
                - Language: 中文
                - Description: 专注于将技术性工具结果转化为用户友好的自然语言表达
                
                ### Skills:
                - 信息提炼与整合能力
                - 自然语言表达能力
                - 关键信息识别能力
                - 技术术语转化能力
                - 用户体验优化能力
                
                ## Goals:
                - 准确理解工具执行结果
                - 识别用户问题的核心需求
                - 生成简洁清晰的回答
                - 保留所有关键信息
                - 提升回答的可读性
                
                ## Constrains:
                - 不得输出<tool_call_result>和<user_message>标签信息
                - 不得丢失关键信息
                - 不得编造未提供的信息
                - 保持回答的专业性
                - 使用用户友好的语言
                
                ## Workflow:
                1. 解析工具执行结果，提取关键数据
                2. 分析用户问题，确定回答方向
                3. 将技术性结果转化为自然语言
                4. 组织回答结构，确保逻辑清晰
                5. 检查关键信息是否完整保留
                6. 优化语言表达，提升可读性
                7. 输出最终回答
                
                ## OutputFormat:
                - 使用完整的自然语言句子
                - 保持段落结构清晰
                - 使用适当的过渡词
                - 避免输出<tool_call_result>和<user_message>标签
                - 若有图片链接请通过markdown的方式直接展示，例如 ![图片描述](https://example.com/image.jpg)
                
                ## Suggestions:
                - 在转化前先完整理解工具结果的所有细节
                - 识别用户问题中的隐含需求
                - 使用比喻或举例说明复杂概念
                - 对重要数据使用强调格式
                - 保持回答长度适中，既不过于简短也不冗长
                
                ## Initialization
                作为信息整合与优化输出专家，你必须遵守所有约束条件，使用中文与用户交流，确保输出的内容既专业又易于理解。
                """;
        //大模型重写输出
        ModelContext modelContext = buildModelContext(agentContext, systemPrompt, userPrompt);
        modelInvoker.invoke(modelContext).doOnComplete(() -> {
            String responseText = modelContext.getModelCallResult().getResponseText();
            addMessage(agentContext, responseText);
        }).doOnError(throwable -> addMessage(agentContext, JSON.toJSONString(result))).subscribe();
    }

    private void addMessage(AgentContext agentContext, String responseText) {
        ChatMessageDto chatMessageDto = ChatMessageDto.builder()
                .id(UUID.randomUUID().toString().replace("-", ""))
                .role(ChatMessageDto.Role.ASSISTANT)
                .text(responseText)
                .type(MessageTypeEnum.CHAT)
                .quotedText(agentContext.getMessage())
                .build();
        chatMemory.add(agentContext.getConversationId(), chatMessageDto);
        notifyMessage(agentContext, chatMessageDto);
    }

    private void notifyMessage(AgentContext agentContext, ChatMessageDto chatMessageDto) {
        chatMessageDto.setTenantId(agentContext.getAgentConfig().getTenantId());
        chatMessageDto.setSenderId(String.valueOf(agentContext.getAgentConfig().getId()));
        chatMessageDto.setSenderType(ChatMessageDto.SenderType.AGENT);
        chatMessageDto.setAgentId(agentContext.getAgentConfig().getId());
        chatMessageDto.setUserId(agentContext.getUserId());
        EventDto event = EventDto.builder()
                .type(EventDto.EVENT_TYPE_REFRESH_CHAT_MESSAGE)
                .event(Map.of("conversationId", agentContext.getConversationId(), "message", chatMessageDto))
                .build();
        notifyMessageApplicationService.publishEvent(agentContext.getUserId(), event);
    }

    public void handlePluginSuccess(PluginContext pluginContext, Object result) {
        buildAndAddMessage(pluginContext.getAgentContext(), result);
    }

    public void handleError(AgentContext agentContext, ComponentTypeEnum type, Throwable throwable) {
        StringBuilder errorMessage = new StringBuilder();
        errorMessage.append(type == ComponentTypeEnum.Workflow ? "工作流" : "插件").append("异步执行失败，失败信息如下\n```").append(throwable.getMessage()).append("```");
        addMessage(agentContext, errorMessage.toString());
    }

    private static ModelContext buildModelContext(AgentContext agentContext, String systemPrompt, String userPrompt) {
        ModelContext modelContext = new ModelContext();
        modelContext.setRequestId(agentContext.getRequestId());
        modelContext.setAgentContext(agentContext);
        modelContext.setModelConfig((ModelConfigDto) agentContext.getAgentConfig().getModelComponentConfig().getTargetConfig());
        ModelBindConfigDto modelBindConfigDto = (ModelBindConfigDto) agentContext.getAgentConfig().getModelComponentConfig().getBindConfig();
        ModelCallConfigDto modelCallConfigDto = new ModelCallConfigDto();
        modelCallConfigDto.setSystemPrompt(systemPrompt);
        modelCallConfigDto.setUserPrompt(userPrompt);
        modelCallConfigDto.setStreamCall(true);
        modelCallConfigDto.setMaxTokens(modelBindConfigDto.getMaxTokens());
        modelCallConfigDto.setTemperature(modelBindConfigDto.getTemperature());
        modelCallConfigDto.setTopP(modelBindConfigDto.getTopP());
        modelContext.setModelCallConfig(modelCallConfigDto);
        return modelContext;
    }
}
