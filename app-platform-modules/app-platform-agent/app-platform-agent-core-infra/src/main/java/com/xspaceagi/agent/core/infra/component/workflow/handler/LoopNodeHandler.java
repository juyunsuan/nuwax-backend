package com.xspaceagi.agent.core.infra.component.workflow.handler;

import cn.hutool.core.collection.ConcurrentHashSet;
import com.xspaceagi.agent.core.adapter.dto.config.Arg;
import com.xspaceagi.agent.core.adapter.dto.config.workflow.LoopNodeConfigDto;
import com.xspaceagi.agent.core.adapter.dto.config.workflow.WorkflowNodeDto;
import com.xspaceagi.agent.core.infra.component.ArgExtractUtil;
import com.xspaceagi.agent.core.infra.component.workflow.WorkflowContext;
import com.xspaceagi.agent.core.infra.component.workflow.WorkflowExecutor;
import com.xspaceagi.agent.core.infra.component.workflow.dto.LoopNodeExecutingDto;
import com.xspaceagi.agent.core.infra.component.workflow.dto.NodeExecuteResult;
import com.xspaceagi.system.spec.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.util.CollectionUtils;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
public class LoopNodeHandler extends AbstractNodeHandler {

    //避免永久死循环
    private int maxLoopTimes = 1000;

    private final List<Map<String, NodeExecuteResult>> nodeExecuteInfoList = new ArrayList<>();

    private final Map<String, Object> variableValueMap = new LinkedHashMap<>();

    private final Map<String, List<Object>> inputValueMap = new LinkedHashMap<>();

    private final AtomicInteger index = new AtomicInteger(0);

    @Override
    public Mono<Object> execute(WorkflowContext workflowContext, WorkflowNodeDto node) {
        AtomicReference<Set<Disposable>> disposableAtomicReferences = new AtomicReference<>(new ConcurrentHashSet<>());
        return Mono.create(emitter0 -> {
            //ode.getInnerNodes() 转Map innerNodeMap
            Map<Long, WorkflowNodeDto> innerNodeMap = node.getInnerNodes().stream().collect(Collectors.toMap(WorkflowNodeDto::getId, n -> n));
            WorkflowNodeDto startNode = innerNodeMap.get(node.getInnerStartNodeId());
            WorkflowNodeDto endNode = innerNodeMap.get(node.getInnerEndNodeId());
            if (startNode == null || endNode == null) {
                emitter0.error(new BizException("循环节点未完整连接"));
                return;
            }
            endNode.setInnerEndNode(true);
            LoopNodeConfigDto loopNodeConfigDto = (LoopNodeConfigDto) node.getNodeConfig();
            //初始化中间变量值
            if (!CollectionUtils.isEmpty(loopNodeConfigDto.getVariableArgs())) {
                createVariableValueMap(workflowContext, loopNodeConfigDto.getVariableArgs());
                workflowContext.getNodeVariableValueMap().put(node.getId().toString(), variableValueMap);
            }
            Map<String, Object> logValueMap = new HashMap<>();
            //初始化入参值
            if (!CollectionUtils.isEmpty(loopNodeConfigDto.getInputArgs())) {
                loopNodeConfigDto.getInputArgs().forEach(inputArg -> {
                    Object value = extraBindValue(workflowContext, node, inputArg);
                    if (value instanceof List<?>) {
                        inputValueMap.put(inputArg.getName(), (List<Object>) value);
                        logValueMap.put(inputArg.getName(), value);
                    }
                });
            }
            workflowContext.getNodeExecuteInputMap().computeIfAbsent(node.getId().toString(), k -> new ArrayList<>()).add(logValueMap);

            //无限循环，暂时最多循环1000次
            maxLoopTimes = 1000;
            //次数循环
            if (loopNodeConfigDto.getLoopType() == LoopNodeConfigDto.LoopTypeEnum.SPECIFY_TIMES_LOOP) {
                maxLoopTimes = loopNodeConfigDto.getLoopTimes();
            } else if (loopNodeConfigDto.getLoopType() == LoopNodeConfigDto.LoopTypeEnum.ARRAY_LOOP) {
                //数组循环，计算数组最大循环次数
                final AtomicInteger maxLoopTimes = new AtomicInteger(0);
                inputValueMap.forEach((key, value) -> {
                    if (value.size() > maxLoopTimes.get()) {
                        maxLoopTimes.set(value.size());
                    }
                });
                this.maxLoopTimes = maxLoopTimes.get();
            }
            if (maxLoopTimes <= 0) {
                emitter0.error(new BizException("循环节点最大循环次数不能小于等于0"));
                return;
            }
            loopExecute(workflowContext, startNode, node, emitter0, disposableAtomicReferences);
        }).doOnCancel(()->{
            disposableAtomicReferences.get().forEach(disposable -> {
                if (!disposable.isDisposed()) {
                    disposable.dispose();
                }
            });
        });
    }

    private void createVariableValueMap(WorkflowContext workflowContext, List<Arg> variableArgs) {
        variableArgs.forEach(variableArg -> {
            if (variableArg.getBindValue() == null) {
                variableArg.setBindValue("");
            }
            if (variableArg.getBindValueType() == Arg.BindValueType.Input) {
                variableValueMap.put(variableArg.getName(), variableArg.getBindValue());
            } else {
                try {
                    String nodeId = variableArg.getBindValue().split("\\.")[0];
                    NodeExecuteResult nodeExecuteInfoDto = workflowContext.getNodeExecuteResultMap().get(nodeId);
                    if (nodeExecuteInfoDto != null && nodeExecuteInfoDto.getData() != null) {
                        Object value = ArgExtractUtil.extraBindValue(Map.of(nodeId, nodeExecuteInfoDto.getData()), variableArg.getBindValue());
                        variableValueMap.put(variableArg.getName(), value);
                    }
                } catch (NumberFormatException e) {
                    log.error("变量绑定值格式错误", e);
                    //ignore
                }
            }
        });
    }

    private void loopExecute(WorkflowContext workflowContext, WorkflowNodeDto startNode, WorkflowNodeDto node, MonoSink<Object> emitter0, AtomicReference<Set<Disposable>> disposableAtomicReferences) {
        LoopNodeExecutingDto loopNodeDto = new LoopNodeExecutingDto();
        BeanUtils.copyProperties(node, loopNodeDto);
        loopNodeDto.setIndex(index.getAndIncrement());
        loopNodeDto.setInputValueMap(inputValueMap);
        Map<String, Object> currentLoopItemValueMap = new HashMap<>();
        if (!inputValueMap.isEmpty()) {
            inputValueMap.forEach((key, value) -> {
                if (index.get() <= value.size()) {
                    currentLoopItemValueMap.put(key + "_item", value.get(index.get() - 1));
                }
            });
        }
        loopNodeDto.setCurrentLoopItemValueMap(currentLoopItemValueMap);
        workflowContext.getExecutingLoopNodeMap().put(node.getId().toString(), loopNodeDto);
        long startTime = System.currentTimeMillis();
        Disposable disposable = Mono.create(emitter -> WorkflowExecutor.executeNode(workflowContext, startNode, loopNodeDto, emitter, disposableAtomicReferences)).subscribe(o -> {
            log.info("循环节点第{}次执行完成，nodeId: {}, nodeName: {}，loopNodeDto: {}, 耗时: {}", loopNodeDto.getIndex(), node.getId(),
                    node.getName(), loopNodeDto, System.currentTimeMillis() - startTime);
            nodeExecuteInfoList.add(loopNodeDto.getNodeExecuteResultMap());
            if (workflowContext.getAgentContext().isInterrupted()) {
                emitter0.success(new ArrayList<>());
                return;
            }
            if (loopNodeDto.isBreakLoop() || index.get() == maxLoopTimes) {
                LoopNodeConfigDto loopNodeConfigDto = (LoopNodeConfigDto) node.getNodeConfig();
                List<Arg> outputArgs = loopNodeConfigDto.getOutputArgs();
                if (CollectionUtils.isEmpty(outputArgs)) {
                    emitter0.success(new ArrayList<>());
                    return;
                }
                Map<String, Object> result = new HashMap<>();
                outputArgs.forEach(outputArg -> {
                    //变量参数、循环前置节点等
                    Object value = extraBindValue(workflowContext, node, outputArg);
                    if (value != null) {
                        result.put(outputArg.getName(), value);
                        return;
                    }

                    if (outputArg.getBindValueType() == Arg.BindValueType.Reference
                            && outputArg.getBindValue() != null && outputArg.getBindValue().contains("-var")) {
                        return;
                    }

                    //循环内部节点
                    List<Object> values = new ArrayList<>();
                    nodeExecuteInfoList.forEach(nodeExecuteInfoMap -> {
                        AtomicBoolean virtualExecuteStatus = new AtomicBoolean(false);
                        Object value0 = extractLoopNodeValue(nodeExecuteInfoMap, outputArg, virtualExecuteStatus);
                        if (!virtualExecuteStatus.get()) {
                            values.add(value0);
                        }
                    });
                    result.put(outputArg.getName(), values);
                });
                emitter0.success(result);
            } else {
                //继续循环执行
                loopExecute(workflowContext, startNode, node, emitter0, disposableAtomicReferences);
            }
        }, e -> {
            log.error("循环节点执行失败", e);
            emitter0.error(e);
        });
        disposableAtomicReferences.get().add(disposable);
    }
}
