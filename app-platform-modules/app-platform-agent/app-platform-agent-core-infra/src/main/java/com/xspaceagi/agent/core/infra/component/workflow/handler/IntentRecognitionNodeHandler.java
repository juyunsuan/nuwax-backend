package com.xspaceagi.agent.core.infra.component.workflow.handler;

import com.alibaba.fastjson2.JSON;
import com.xspaceagi.agent.core.adapter.dto.config.workflow.IntentRecognitionNodeConfigDto;
import com.xspaceagi.agent.core.adapter.dto.config.workflow.WorkflowNodeDto;
import com.xspaceagi.agent.core.infra.component.model.ModelContext;
import com.xspaceagi.agent.core.infra.component.model.ModelInvoker;
import com.xspaceagi.agent.core.infra.component.model.dto.ModelCallConfigDto;
import com.xspaceagi.agent.core.infra.component.workflow.WorkflowContext;
import com.xspaceagi.agent.core.spec.enums.OutputTypeEnum;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class IntentRecognitionNodeHandler extends AbstractNodeHandler {

    private static final String INTENT_RECOGNITION_SYSTEM_PROMPT = """
            请根据下面提供的信息，识别意图，并将其与预设意图选项进行匹配，若匹配不上时选择"其他意图"。
            """;

    @Override
    public Mono<Object> execute(WorkflowContext workflowContext, WorkflowNodeDto node) {
        IntentRecognitionNodeConfigDto intentRecognitionNodeConfigDto = (IntentRecognitionNodeConfigDto) node.getNodeConfig();
        Map<String, Object> params = extraBindValueMap(workflowContext, node, intentRecognitionNodeConfigDto.getInputArgs());
        ModelContext modelContext = new ModelContext();
        modelContext.setAgentContext(workflowContext.getCopiedAgentContext());
        modelContext.setConversationId(workflowContext.getAgentContext().getConversationId());
        modelContext.setModelConfig(intentRecognitionNodeConfigDto.getModelConfig());
        ModelCallConfigDto modelCallConfigDto = new ModelCallConfigDto();
        AtomicInteger classificationIndex = new AtomicInteger(0);
        List<Map<String, Object>> intentOptions = intentRecognitionNodeConfigDto.getIntentConfigs().stream().collect(ArrayList::new, (list, intentConfigDto) -> {
            Map<String, Object> intentOption = new HashMap<>();
            intentOption.put("classificationId", classificationIndex.getAndIncrement());
            intentOption.put("intent", intentConfigDto.getIntent());
            list.add(intentOption);
        }, ArrayList::addAll);
        StringBuilder stringBuilder = new StringBuilder(INTENT_RECOGNITION_SYSTEM_PROMPT).append("\n");
        if (StringUtils.isNotBlank(intentRecognitionNodeConfigDto.getExtraPrompt())) {
            stringBuilder.append(intentRecognitionNodeConfigDto.getExtraPrompt()).append("\n");
        }
        if (intentRecognitionNodeConfigDto.getUseHistory() != null && intentRecognitionNodeConfigDto.getUseHistory()) {
            ChatMemory chatMemory = workflowContext.getWorkflowContextServiceHolder().getChatMemory();
            List<Message> messages = chatMemory.get(workflowContext.getAgentContext().getConversationId(), 12);
            if (CollectionUtils.isNotEmpty(messages)) {
                stringBuilder.append("对话历史:\n");
                messages.forEach(message -> stringBuilder.append(message.getMessageType().name()).append(":").append(message.getText()).append("\n"));
            }
        }
        stringBuilder.append("识别参数：\n").append(JSON.toJSONString(params)).append("\n");
        stringBuilder.append("意图选项：\n").append(JSON.toJSONString(intentOptions));
        modelCallConfigDto.setSystemPrompt(stringBuilder.toString());
        modelCallConfigDto.setUserPrompt("");
        modelCallConfigDto.setChatRound(0);
        modelCallConfigDto.setStreamCall(true);
        modelCallConfigDto.setOutputType(OutputTypeEnum.JSON);

        //构建意图识别的默认出参
        var outputArgs = intentRecognitionNodeConfigDto.getOutputArgs();

        modelCallConfigDto.setOutputArgs(outputArgs);
        modelCallConfigDto.setMaxTokens(intentRecognitionNodeConfigDto.getMaxTokens());
        modelCallConfigDto.setTemperature(intentRecognitionNodeConfigDto.getTemperature());
        modelCallConfigDto.setTopP(intentRecognitionNodeConfigDto.getTopP());
        modelContext.setModelCallConfig(modelCallConfigDto);
        ModelInvoker modelInvoker = workflowContext.getWorkflowContextServiceHolder().getModelInvoker();
        return modelInvoker.invoke(modelContext).last().map(result -> {
            Set<Long> reachableNextNodeIds = new HashSet<>();
            Set<Long> unReachableNextNodeIds = new HashSet<>();
            Object data = modelContext.getModelCallResult().getData();
            int classificationId = classificationIndex.get();
            Object reason = "未识别到意图，选择其他意图";
            if ((data instanceof Map<?, ?>)) {
                Map<String, Object> output = (Map<String, Object>) data;
                Object classificationId0 = output.get("classificationId");
                if (classificationId0 != null) {
                    try {
                        classificationId = classificationId0 instanceof Number ? ((Number) classificationId0).intValue() : Integer.parseInt(classificationId0.toString());
                        reason = output.get("reason");
                    } catch (NumberFormatException e) {
                        classificationId = classificationIndex.get();
                    }
                }
            }

            final Integer classificationId0 = classificationId;
            //intentOptions中过滤出classificationId相同的选项
            Map<String, Object> optional = intentOptions.stream().filter(intentOption -> classificationId0.equals(intentOption.get("classificationId"))).findFirst().orElse(new HashMap<>());
            if (optional.isEmpty()) {
                optional.put("classificationId", classificationIndex.get());
                optional.put("intent", "其他意图");
                reason = "未识别到意图，选择其他意图";
            }
            intentRecognitionNodeConfigDto.getIntentConfigs().forEach(intentConfigDto -> {
                if (intentConfigDto.getIntent().equals(optional.get("intent"))) {
                    reachableNextNodeIds.addAll(intentConfigDto.getNextNodeIds());
                } else {
                    unReachableNextNodeIds.addAll(intentConfigDto.getNextNodeIds());
                }
            });
            unReachableNextNodeIds.removeAll(reachableNextNodeIds);
            node.setUnreachableNextNodeIds(unReachableNextNodeIds);
            return Map.of("classificationId", classificationId, "reason", reason);
        });
    }
}
