package com.xspaceagi.custompage.domain.service.impl;

import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xspaceagi.agent.core.adapter.application.ModelApplicationService;
import com.xspaceagi.agent.core.adapter.dto.AttachmentDto;
import com.xspaceagi.agent.core.adapter.dto.config.ModelConfigDto;
import com.xspaceagi.agent.core.infra.component.agent.AgentContext;
import com.xspaceagi.agent.core.infra.component.model.ModelContext;
import com.xspaceagi.agent.core.infra.component.model.ModelInvoker;
import com.xspaceagi.agent.core.infra.component.model.dto.ModelCallConfigDto;
import com.xspaceagi.agent.core.infra.rpc.MarketClientRpcService;
import com.xspaceagi.agent.core.sdk.IAgentRpcService;
import com.xspaceagi.agent.core.sdk.enums.TargetTypeEnum;
import com.xspaceagi.agent.core.spec.enums.DataTypeEnum;
import com.xspaceagi.agent.core.spec.enums.ModelTypeEnum;
import com.xspaceagi.agent.core.spec.enums.OutputTypeEnum;
import com.xspaceagi.agent.core.spec.utils.UrlFile;
import com.xspaceagi.custompage.domain.gateway.AiAgentClient;
import com.xspaceagi.custompage.domain.gateway.PageFileBuildClient;
import com.xspaceagi.custompage.domain.model.CustomPageBuildModel;
import com.xspaceagi.custompage.domain.model.CustomPageConfigModel;
import com.xspaceagi.custompage.domain.repository.ICustomPageBuildRepository;
import com.xspaceagi.custompage.domain.repository.ICustomPageConfigRepository;
import com.xspaceagi.custompage.domain.service.CustomPageChatSessionManager;
import com.xspaceagi.custompage.domain.service.ICustomPageChatFluxService;
import com.xspaceagi.custompage.sdk.dto.DataSourceDto;
import com.xspaceagi.custompage.sdk.dto.VersionInfoDto;
import com.xspaceagi.custompage.sdk.enums.CustomPageActionEnum;
import com.xspaceagi.eco.market.sdk.reponse.ClientSecretResponse;
import com.xspaceagi.eco.market.sdk.request.ClientSecretRequest;
import com.xspaceagi.system.application.dto.TenantConfigDto;
import com.xspaceagi.system.sdk.permission.SpacePermissionService;
import com.xspaceagi.system.spec.common.RequestContext;
import com.xspaceagi.system.spec.common.UserContext;
import com.xspaceagi.system.spec.exception.BizException;
import com.xspaceagi.system.spec.file.FileSystemMultipartFile;
import com.xspaceagi.system.spec.utils.DateUtil;
import com.xspaceagi.system.spec.utils.FileAkUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class CustomPageChatFluxServiceImpl implements ICustomPageChatFluxService {

    @Resource
    private FileAkUtil fileAkUtil;
    @Resource
    private AiAgentClient aiAgentClient;
    @Resource
    private IAgentRpcService agentRpcService;
    @Resource
    private PageFileBuildClient pageFileBuildClient;
    @Resource
    private SpacePermissionService spacePermissionService;
    @Resource
    private ModelApplicationService modelApplicationService;
    @Resource
    private ICustomPageBuildRepository customPageBuildRepository;
    @Resource
    private ICustomPageConfigRepository customPageConfigRepository;
    @Resource
    private CustomPageChatSessionManager sessionManager;
    @Resource
    private MarketClientRpcService marketClientRpcService;
    @Resource
    @Qualifier("aiChatCallExecutor")
    private Executor aiChatCallExecutor;

    @Resource
    private ModelInvoker modelInvoker;

    private final static ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);


    @Override
    public Flux<Map<String, Object>> sendAgentChatFlux(Map<String, Object> chatBody, UserContext userContext) {
        // 验证参数
        if (chatBody == null) {
            return Flux.error(new IllegalArgumentException("请求体不能为空"));
        }

        Long projectId;
        Object projectIdObj = chatBody.get("project_id");
        Object promptObj = chatBody.get("prompt");

        if (projectIdObj == null) {
            return Flux.error(new IllegalArgumentException("project_id不能为空"));
        }
        if (promptObj == null || StringUtils.isBlank(String.valueOf(promptObj))) {
            return Flux.error(new IllegalArgumentException("prompt不能为空"));
        }
        try {
            projectId = Long.valueOf(String.valueOf(projectIdObj));
        } catch (Exception e) {
            return Flux.error(new IllegalArgumentException("project_id 非法"));
        }
        CustomPageBuildModel buildModel = customPageBuildRepository.getByProjectId(projectId);
        if (buildModel == null) {
            return Flux.error(new IllegalArgumentException("项目不存在"));
        }
        spacePermissionService.checkSpaceUserPermission(buildModel.getSpaceId());

        // 生成会话ID
        String sessionId = UUID.randomUUID().toString().replace("-", "");

        return Flux.<Map<String, Object>>create(sink -> {
            try {
                // 注册会话
                sessionManager.registerSession(sessionId, sink);

                // 发送会话ID
                sendSessionIdFlux(sink, sessionId);

                executeChatFlux(chatBody, userContext, sink, buildModel, promptObj);
            } catch (Exception e) {
                log.error("[FluxService] 流式聊天执行异常", e);
                sink.error(e);
            } finally {
                // 完成流并移除会话
                try {
                    sink.complete();
                } catch (Exception e) {
                    log.debug("[FluxService] sink 已完成，忽略重复调用", e);
                }
                sessionManager.removeSession(sessionId);
            }
        }).onErrorResume(error -> {
            Map<String, Object> errorMap = new HashMap<>();
            errorMap.put("type", "error");
            errorMap.put("code", "0001");
            errorMap.put("message", error.getMessage());
            return Flux.just(errorMap);
        });
    }

    private void executeChatFlux(Map<String, Object> chatBody, UserContext userContext,
                                 FluxSink<Map<String, Object>> sink, CustomPageBuildModel buildModel, Object promptObj) {
        try {
            Long projectId = buildModel.getProjectId();

            // 1: 处理原型图片
            Long multiModelId = processPrototypeImagesFlux(chatBody, projectId, sink);

            // 2: 处理附件文件
            processAttachmentFilesFlux(chatBody, projectId, promptObj, sink);

            // 3: 处理模型配置
            Long chatModelId = processModelConfigFlux(chatBody, sink);

            // 4: 处理数据源
            processDataSourcesFlux(chatBody, projectId, sink);

            // 5: 备份当前版本
            // sendProgressFlux(sink, "正在备份当前版本...", 60);
            sendHeartbeatFlux(sink);

            Integer currentVersion = buildModel.getCodeVersion() == null ? 0 : buildModel.getCodeVersion();
            Map<String, Object> backupResp = pageFileBuildClient.backupCurrentVersion(projectId, currentVersion);
            if (backupResp == null || !Boolean.parseBoolean(String.valueOf(backupResp.get("success")))) {
                String msg = backupResp != null && backupResp.get("message") != null
                        ? String.valueOf(backupResp.get("message"))
                        : "备份失败";
                sendErrorFlux(sink, "9999", msg);
                return;
            }

            // 6: 更新版本
            // sendProgressFlux(sink, "正在更新版本...", 70);
            sendHeartbeatFlux(sink);

            Integer nextVersion = currentVersion + 1;
            List<VersionInfoDto> versionInfo = buildModel.getVersionInfo();
            // 仅记录提示词前100个字符到ext
            String promptStr = String.valueOf(promptObj);
            String briefPrompt = promptStr.length() > 100 ? promptStr.substring(0, 100) : promptStr;
            Map<String, String> ext = new HashMap<>();
            ext.put("prompt", briefPrompt);
            versionInfo.add(VersionInfoDto.builder()
                    .version(nextVersion)
                    .time(DateUtil.format(new Date(), "yyyy-MM-dd HH:mm:ss"))
                    .action(CustomPageActionEnum.CHAT.getCode())
                    .ext(ext)
                    .build());

            CustomPageBuildModel updateModel = new CustomPageBuildModel();
            updateModel.setId(buildModel.getId());
            updateModel.setCodeVersion(nextVersion);
            updateModel.setVersionInfo(versionInfo);
            updateModel.setLastChatModelId(chatModelId);
            updateModel.setLastMultiModelId(multiModelId);
            customPageBuildRepository.updateVersionInfo(updateModel, userContext);

            // 7: 调用 AI Agent
            //sendProgressFlux(sink, "正在与 AI 对话...", 80);
            sendHeartbeatFlux(sink, "Calling AI agent...80%");

            // 异步调用 sendChat，并在等待期间发送心跳
            //Map<String, Object> chatResp = callSendChatWithHeartbeat(chatBody, sink);
            Map<String, Object> chatResp = callSendChatSync(chatBody);
            if (chatResp == null) {
                sendErrorFlux(sink, "9999", "AI Agent 无响应");
                return;
            }

            Object code = chatResp.get("code");
            if (code == null || !"0000".equals(String.valueOf(code))) {
                String errorCode = code != null ? String.valueOf(code) : "9999";
                sendErrorFlux(sink, errorCode, String.valueOf(chatResp.get("message")));
                return;
            }

            // 8: 返回结果
            //sendProgressFlux(sink, "AI 处理中...", 100);
            sendHeartbeatFlux(sink, "AI returned result...100%");

            Map<String, Object> dataMap = parseResponseData(chatResp);
            sendSuccessFlux(sink, dataMap);

        } catch (Exception e) {
            log.error("[FluxService] Flux 流式聊天执行异常", e);
            sendErrorFlux(sink, "0001", "执行失败: " + e.getMessage());
        }
    }

    private Long processPrototypeImagesFlux(Map<String, Object> chatBody, Long projectId,
                                            FluxSink<Map<String, Object>> sink) {
        Long multiModelId = null;
        Object multiModelIdObj = chatBody.get("multi_model_id");
        if (multiModelIdObj == null) {
            log.info("[FluxService] 发送聊天消息,projectId={},传了原型图片,但未传多模态模型ID", projectId);

            // 从 RequestContext 获取租户配置
            TenantConfigDto tenantConfig = (TenantConfigDto) RequestContext.get().getTenantConfig();
            if (tenantConfig == null || tenantConfig.getDefaultVisualModelId() == null
                    || tenantConfig.getDefaultVisualModelId() == 0) {
                log.info("[FluxService] 发送聊天消息,projectId={},没有配置默认多模态模型,不解析", projectId);
                return multiModelId;
            } else {
                multiModelId = tenantConfig.getDefaultVisualModelId();
            }
        } else {
            multiModelId = Long.valueOf(String.valueOf(multiModelIdObj));
        }

        Object prototypeImages = chatBody.get("attachment_prototype_images");
        if (!(prototypeImages instanceof List<?>) || ((List<?>) prototypeImages).isEmpty()) {
            return multiModelId;
        }
        try {
            modelApplicationService.checkModelUsePermission(multiModelId);
        } catch (Exception e) {
            log.warn("[FluxService] 当前多模态模型不可用：{}", e.getMessage());
            throw new BizException("当前多模态模型不可用：" + e.getMessage());
        }
        ModelConfigDto modelConfig = modelApplicationService.queryModelConfigById(multiModelId);

        if (modelConfig.getType() != ModelTypeEnum.Multi) {
            log.warn("[FluxService] 当前模型不支持多模态，无法解析图片，模型 id={}", multiModelId);
            throw new BizException("当前模型不支持多模态，无法解析图片，模型 id=" + multiModelId);
        }

        log.info("[FluxService] 发送聊天消息,projectId={},prototypeImages={}", projectId, JSON.toJSONString(prototypeImages));

        Object promptObj = chatBody.get("prompt");

        for (Object prototypeImage : (List<?>) prototypeImages) {
            if (prototypeImage instanceof Map<?, ?> m) {
                Object urlObj = m.get("url");
                Object fileNameObj = m.get("fileName");
                Object mimeTypeObj = m.get("mimeType");
                Object fileKeyObj = m.get("fileKey");

                if (urlObj == null) {
                    throw new IllegalArgumentException("附件URL不能为空");
                }
                if (mimeTypeObj == null) {
                    throw new IllegalArgumentException("附件类型不能为空");
                }

                sendProgressFlux(sink, "开始解析原型图片[" + fileNameObj + "]", 10);

                AttachmentDto attachmentDto = new AttachmentDto();
                attachmentDto.setFileUrl(String.valueOf(urlObj));
                attachmentDto.setMimeType(String.valueOf(mimeTypeObj));
                attachmentDto.setFileName(fileNameObj != null ? String.valueOf(fileNameObj) : null);
                attachmentDto.setFileKey(fileKeyObj != null ? String.valueOf(fileKeyObj) : null);

                AgentContext agentContext = new AgentContext();
                agentContext.setAttachments(List.of(attachmentDto));
                agentContext.setRequestId(UUID.randomUUID().toString());

                ModelContext modelContext = new ModelContext();
                modelContext.setAgentContext(agentContext);
                modelContext.setRequestId(agentContext.getRequestId());
                modelContext.setModelConfig(modelConfig);

                ModelCallConfigDto modelCallConfig = new ModelCallConfigDto();
                modelCallConfig.setSystemPrompt(
                        "你是一个专业的原型图分析助手，专门将UI原型图转换为结构化的Markdown描述，供AI编码工具生成网页代码。你的任务是准确识别页面布局、UI组件、样式和交互元素，并用清晰、结构化的Markdown格式输出。");
                modelCallConfig.setUserPrompt(
                        "请分析这张UI原型图，识别并描述以下内容，使用Markdown格式输出：\n\n## 页面整体布局\n- 描述页面的整体布局结构（如：顶部导航栏、侧边栏、主内容区等）\n- 说明各组件的层级关系和位置关系\n\n## UI组件详情\n对于每个重要的UI组件，请描述：\n- 组件类型（如：按钮、输入框、表格、卡片、列表等）\n- 组件位置和尺寸\n- 组件内容（文字、图标等）\n- 组件样式（颜色、字体大小、边框、圆角等）\n\n## 样式信息\n- 主色调和辅助色\n- 字体大小和字重\n- 间距和边距\n- 圆角、阴影等视觉效果\n\n## 交互说明\n- 按钮点击效果\n- 表单输入说明\n- 其他交互提示\n\n请确保输出清晰、准确、结构完整，便于编码工具理解并生成对应的网页代码。");
                modelCallConfig.setOutputType(OutputTypeEnum.Markdown);
                modelCallConfig.setStreamCall(true);
                modelContext.setModelCallConfig(modelCallConfig);

                // 调用多模态模型
                CountDownLatch latch = new CountDownLatch(1);
                AtomicReference<Throwable> throwableAtomicReference = new AtomicReference<>();
                modelInvoker.invoke(modelContext).timeout(Duration.ofSeconds(300))
                        .doOnComplete(() -> latch.countDown())
                        .subscribe(callMessage -> {
                            sendProgressFlux(sink, callMessage.getText(), 10);
                        }, throwable -> {
                            throwableAtomicReference.set(throwable);
                            latch.countDown();
                        });
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                if (throwableAtomicReference.get() != null) {
                    throw new RuntimeException(throwableAtomicReference.get());
                }
                String markdownContent = modelContext.getModelCallResult().getResponseText();
                log.info("[FluxService] projectId={} 原型图片解析完成,url={}", projectId, urlObj);

                // 发送图片解析结果事件
                sendImageAnalysisResultFlux(sink, String.valueOf(urlObj), String.valueOf(fileNameObj), markdownContent);

                // 将解析结果添加到聊天体中
                chatBody.put("prompt", promptObj + "\n" + markdownContent);
            }
        }
        return multiModelId;
    }

    private void processAttachmentFilesFlux(Map<String, Object> chatBody, Long projectId, Object promptObj,
                                            FluxSink<Map<String, Object>> sink) {
        Object attachmentFiles = chatBody.get("attachment_files");
        if (attachmentFiles == null) {
            return;
        }

        log.info("[FluxService] 发送聊天消息,projectId={},开始处理附件,files={}", projectId, JSON.toJSONString(attachmentFiles));

        for (Object attachment : (List<?>) attachmentFiles) {
            if (attachment instanceof Map<?, ?> m) {
                Object urlObj = m.get("url");
                Object fileNameObj = m.get("fileName");
                if (urlObj == null) {
                    throw new IllegalArgumentException("附件URL不能为空");
                }
                if (fileNameObj == null) {
                    throw new IllegalArgumentException("附件文件名不能为空");
                }

                // 发送心跳
                sendHeartbeatFlux(sink);

                sendProgressFlux(sink, "正在解析附件[" + fileNameObj + "]...", 30);

                String outputPrompt = parseFileToText(projectId, String.valueOf(urlObj), String.valueOf(fileNameObj));
                if (outputPrompt != null && !outputPrompt.isEmpty()) {
                    chatBody.put("prompt", promptObj + "\n" + outputPrompt);
                }
            }
        }
    }

    private Long processModelConfigFlux(Map<String, Object> chatBody,
                                        FluxSink<Map<String, Object>> sink) {
        Long projectId = Long.valueOf(String.valueOf(chatBody.get("project_id")));
        Object chatModelIdObj = chatBody.get("chat_model_id");
        log.info("[FluxService] 发送聊天消息,projectId={},chatModelId={}", projectId, chatModelIdObj);

        Long chatModelId;
        if (chatModelIdObj == null) {
            log.info("[FluxService] 发送聊天消息,没有配置聊天模型");

            // 从 RequestContext 获取租户配置
            TenantConfigDto tenantConfig = (TenantConfigDto) RequestContext.get().getTenantConfig();
            if (tenantConfig == null || tenantConfig.getDefaultCodingModelId() == null
                    || tenantConfig.getDefaultCodingModelId() == 0) {
                log.info("[FluxService] 发送聊天消息,projectId={},没有配置默认聊天模型,不解析", projectId);
                throw new IllegalArgumentException("没有配置默认聊天模型");
            } else {
                chatModelId = tenantConfig.getDefaultCodingModelId();
            }
        } else {
            chatModelId = Long.valueOf(String.valueOf(chatModelIdObj));
        }
        try {
            modelApplicationService.checkModelUsePermission(chatModelId);
        } catch (Exception e) {
            log.warn("[FluxService] 当前聊天模型不可用：{}", e.getMessage());
            throw new BizException("当前聊天模型不可用：" + e.getMessage());
        }
        // sendProgressFlux(sink, "正在配置模型...", 40);
        sendHeartbeatFlux(sink);

        ModelConfigDto modelConfigDto = modelApplicationService.queryModelConfigById(chatModelId);

        // 按照权重随机选择一个 ApiInfo
        ModelConfigDto.ApiInfo selectedApiInfo = selectByWeight(modelConfigDto.getApiInfoList());

        if (selectedApiInfo.getKey() != null && selectedApiInfo.getKey().contains("TENANT_SECRET")) {
            ClientSecretRequest clientSecretRequest = new ClientSecretRequest();
            clientSecretRequest.setTenantId(modelConfigDto.getTenantId());
            ClientSecretResponse clientSecretResponse = marketClientRpcService.queryClientSecret(clientSecretRequest);
            selectedApiInfo.setKey(selectedApiInfo.getKey().replace("TENANT_SECRET", clientSecretResponse.getClientSecret()));
        }

        Map<String, Object> modelProvider = new HashMap<>();
        modelProvider.put("api_key", selectedApiInfo.getKey());
        modelProvider.put("api_protocol", modelConfigDto.getApiProtocol().name());
        modelProvider.put("base_url", selectedApiInfo.getUrl().replace("SESSION_ID", UUID.randomUUID().toString().replace("-", "")));
        modelProvider.put("default_model", modelConfigDto.getModel());
        modelProvider.put("id", modelConfigDto.getId().toString() + "_"
                + (modelConfigDto.getModified() == null ? 0 : modelConfigDto.getModified().getTime()));
        modelProvider.put("name", modelConfigDto.getName());
        modelProvider.put("requires_openai_auth", true);

        chatBody.put("model_provider", modelProvider);

        return chatModelId;
    }

    private ModelConfigDto.ApiInfo selectByWeight(List<ModelConfigDto.ApiInfo> apiInfoList) {
        if (apiInfoList == null || apiInfoList.isEmpty()) {
            throw new IllegalArgumentException("模型API列表为空");
        }
        if (apiInfoList.size() == 1) {
            return apiInfoList.get(0);
        }
        long totalWeight = 0;
        for (ModelConfigDto.ApiInfo apiInfo : apiInfoList) {
            int w = apiInfo.getWeight() == null ? 1 : apiInfo.getWeight();
            if (w < 0) {
                w = 0;
            }
            totalWeight += w;
        }
        if (totalWeight <= 0) {
            // 所有权重都无效，退化为均匀随机
            int idx = ThreadLocalRandom.current().nextInt(apiInfoList.size());
            return apiInfoList.get(idx);
        }
        long r = ThreadLocalRandom.current().nextLong(1, totalWeight + 1);
        long cum = 0;
        for (ModelConfigDto.ApiInfo apiInfo : apiInfoList) {
            int w = apiInfo.getWeight() == null ? 1 : apiInfo.getWeight();
            if (w < 0) {
                w = 0;
            }
            cum += w;
            if (r <= cum) {
                return apiInfo;
            }
        }
        return apiInfoList.get(apiInfoList.size() - 1);
    }

    private void processDataSourcesFlux(Map<String, Object> chatBody, Long projectId,
                                        FluxSink<Map<String, Object>> sink) {
        Object dataSources = chatBody.get("data_sources");
        if (dataSources == null) {
            return;
        }

        log.info("[FluxService] 发送聊天消息,projectId={},dataSources={}", projectId, JSON.toJSONString(dataSources));

        List<DataSourceDto> dataSourceList = new ArrayList<>();
        if (dataSources instanceof List<?>) {
            // sendProgressFlux(sink, "正在处理数据源...", 50);
            sendHeartbeatFlux(sink);

            for (Object ds : (List<?>) dataSources) {
                if (ds instanceof Map<?, ?> m) {
                    DataSourceDto dataSource = new DataSourceDto();
                    Object type = m.get("type");
                    Object dataSourceId = m.get("dataSourceId");
                    if (type != null) {
                        dataSource.setType(String.valueOf(type));
                    }
                    if (dataSourceId != null) {
                        dataSource.setId(Long.valueOf(String.valueOf(dataSourceId)));
                    }
                    dataSourceList.add(dataSource);
                }
            }

            if (dataSourceList.size() > 0) {
                CustomPageConfigModel configModel = customPageConfigRepository.getById(projectId);
                if (configModel == null) {
                    throw new IllegalArgumentException("项目配置不存在: " + projectId);
                }
                List<DataSourceDto> existingDataSources = Optional.ofNullable(configModel.getDataSources())
                        .orElseThrow(() -> new IllegalArgumentException("项目未绑定任何数据源"));

                // 判断传入的dataSourceList是否都在existingDataSources中
                for (DataSourceDto incoming : dataSourceList) {
                    boolean found = existingDataSources.stream()
                            .anyMatch(existing -> existing.getId() != null
                                    && existing.getId().equals(incoming.getId())
                                    && existing.getType() != null && existing.getType().equals(incoming.getType()));
                    if (!found) {
                        throw new IllegalArgumentException(
                                "数据源未授权: dataSouceId=" + incoming.getId() + ", type=" + incoming.getType());
                    }
                }

                List<String> dataSourceSchemaList = new ArrayList<>();

                for (DataSourceDto incoming : dataSourceList) {
                    String type = incoming.getType();
                    Long id = incoming.getId();

                    TargetTypeEnum typeEnum = "plugin".equals(String.valueOf(type))
                            ? TargetTypeEnum.Plugin
                            : "workflow".equals(String.valueOf(type))
                            ? TargetTypeEnum.Workflow
                            : null;
                    if (typeEnum == null) {
                        throw new IllegalArgumentException("数据源类型不支持: " + type);
                    }

                    // 发送心跳
                    sendHeartbeatFlux(sink);

                    com.xspaceagi.agent.core.sdk.dto.ReqResult<String> queryApiSchemaResult = agentRpcService
                            .queryApiSchema(typeEnum, id, projectId);
                    if (!queryApiSchemaResult.isSuccess()) {
                        throw new IllegalArgumentException("查询数据源Schema失败: " + queryApiSchemaResult.getMessage());
                    }

                    String dataSourceSchema = queryApiSchemaResult.getData();
                    dataSourceSchemaList.add(dataSourceSchema);
                }
                log.info("[FluxService] 发送聊天消息,projectId={},数据源提示={}", projectId, dataSourceSchemaList);
                chatBody.put("data_source_attachments", dataSourceSchemaList);
            }
        }
    }

    private void sendProgressFlux(FluxSink<Map<String, Object>> sink, String message, int progress) {
        Map<String, Object> data = new HashMap<>();
        data.put("type", "progress");
        data.put("message", message);
        data.put("progress", progress);
        sink.next(data);
        log.info("[FluxService] Flux 发送进度: {}", message);
    }

    private void sendSessionIdFlux(FluxSink<Map<String, Object>> sink, String sessionId) {
        Map<String, Object> data = new HashMap<>();
        data.put("type", "session_id");
        data.put("session_id", sessionId);
        sink.next(data);
        log.info("[FluxService] Flux 发送会话ID: {}", sessionId);
    }

    private void sendHeartbeatFlux(FluxSink<Map<String, Object>> sink) {
        sendHeartbeatFlux(sink, null);
    }

    private void sendHeartbeatFlux(FluxSink<Map<String, Object>> sink, String remark) {
        Map<String, Object> data = new HashMap<>();
        data.put("type", "heartbeat");
        data.put("timestamp", System.currentTimeMillis());
        if (remark != null) {
            data.put("remark", remark);
        }
        sink.next(data);
        log.debug("[FluxService] Flux 发送心跳");
    }

    /**
     * 同步调用 sendChat，带超时机制，不发送心跳
     */
    private Map<String, Object> callSendChatSync(Map<String, Object> chatBody) {
        try {
            CompletableFuture<Map<String, Object>> future = CompletableFuture.supplyAsync(() -> {
                return aiAgentClient.sendChat(chatBody);
            }, aiChatCallExecutor);

            long timeoutMs = 65000; // 65秒超时（略大于RestTemplate的60秒超时）
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.warn("[FluxService] AI Agent 同步调用超时，已等待 65 秒", e);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[FluxService] AI Agent 同步调用被中断", e);
            return null;
        } catch (Exception e) {
            log.error("[FluxService] 同步调用 AI Agent 异常", e);
            return null;
        }
    }

    private void sendSuccessFlux(FluxSink<Map<String, Object>> sink, Map<String, Object> data) {
        Map<String, Object> result = new HashMap<>();
        result.put("type", "success");
        result.put("data", data);
        sink.next(result);
        log.info("[FluxService] Flux 发送成功结果");
    }

    private void sendErrorFlux(FluxSink<Map<String, Object>> sink, String code, String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("type", "error");
        result.put("code", code);
        result.put("message", message);
        sink.next(result);
        // 不在这里调用 complete()，由 finally 块统一处理
        log.error("[FluxService] Flux 发送错误: code={}, message={}", code, message);
    }

    private void sendImageAnalysisResultFlux(FluxSink<Map<String, Object>> sink, String imageUrl,
                                             String fileName, String analysisResult) {
        Map<String, Object> data = new HashMap<>();
        data.put("type", "image_analysis");
        data.put("imageUrl", imageUrl);
        data.put("fileName", fileName);
        data.put("analysisResult", analysisResult);
        data.put("timestamp", System.currentTimeMillis());
        sink.next(data);
        log.info("[FluxService] Flux 发送图片解析结果: fileName={}", fileName);
    }

    private Map<String, Object> parseResponseData(Map<String, Object> chatResp) {
        Object data = chatResp.get("data");
        Map<String, Object> dataMap = new HashMap<>();

        if (data != null) {
            try {
                if (data instanceof Map<?, ?>) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> existingMap = (Map<String, Object>) data;
                    dataMap = existingMap;
                } else {
                    String dataJson = data.toString();
                    dataMap = objectMapper.readValue(dataJson, new TypeReference<Map<String, Object>>() {
                    });
                }
            } catch (Exception e) {
                log.warn("解析data JSON失败，使用原始数据", e);
                dataMap.put("data", data);
            }
        }

        if (chatResp.get("tid") != null) {
            dataMap.put("tid", chatResp.get("tid"));
        }
        if (chatResp.get("message") != null) {
            dataMap.put("message", chatResp.get("message"));
        }
        if (chatResp.get("code") != null) {
            dataMap.put("code", chatResp.get("code"));
        }

        return dataMap;
    }

    private String parseFileToText(Long projectId, String url, String fileName) {
        DataTypeEnum dataType = getDocumentTypeFromUrl(url);
        if (dataType == null) {
            log.warn("[FluxService] projectId={} 获取文件类型失败,url={}", projectId, url);
            return null;
        }

        String output;
        try {
            switch (dataType) {
                case File_Doc:
                    try {
                        log.info("[FluxService] projectId={} 开始解析Word文档, url={}", projectId, url);
                        String textContent = UrlFile.wordToMarkdown(url);
                        File tempFile = writeTextToTempFile(projectId, textContent, fileName);
                        output = uploadFileAndGeneratePrompt(projectId, tempFile, fileName,
                                "application/msword", "Word文档附件", true);
                    } catch (Exception e) {
                        log.warn("[FluxService] projectId={} Word文档处理失败, url={}", projectId, url, e);
                        output = "";
                    }
                    break;
                case File_Excel:
                    try {
                        log.info("[FluxService] projectId={} 开始解析Excel文档, url={}", projectId, url);
                        String textContent = UrlFile.excelToJson(url);
                        File tempFile = writeTextToTempFile(projectId, textContent, fileName);
                        output = uploadFileAndGeneratePrompt(projectId, tempFile, fileName, "application/vnd.ms-excel", "Excel文档附件", true);
                    } catch (Exception e) {
                        log.warn("[FluxService] projectId={} Excel文档处理失败, url={}", projectId, url, e);
                        output = "";
                    }
                    break;
                case File_Txt:
                    try {
                        log.info("[FluxService] projectId={} 开始解析Txt文档, url={}", projectId, url);
                        String textContent = UrlFile.urlToText(url, "UTF-8");
                        File tempFile = writeTextToTempFile(projectId, textContent, fileName);
                        output = uploadFileAndGeneratePrompt(projectId, tempFile, fileName, "text/plain", "文本文件附件", true);
                    } catch (Exception e) {
                        log.warn("[FluxService] projectId={} 文本文件处理失败, url={}", projectId, url, e);
                        output = "";
                    }
                    break;
                case File_Image:
                    try {
                        log.info("[FluxService] projectId={} 开始上传图片, url={}", projectId, url);
                        File tempFile = downloadUrlToTempFile(projectId, url, fileName);
                        output = uploadFileAndGeneratePrompt(projectId, tempFile, fileName, "image/*", "图片附件", false);
                        output += "\n请将使用到的图片放置到资源目录(src/assets/)下使用，使用相对路径引用图片。";
                    } catch (Exception e) {
                        log.warn("[FluxService] projectId={} 图片上传失败, url={}", projectId, url, e);
                        output = "";
                    }
                    break;
                case File_Svg:
                    try {
                        log.info("[FluxService] projectId={} 开始上传SVG, url={}", projectId, url);
                        File tempFile = downloadUrlToTempFile(projectId, url, fileName);
                        output = uploadFileAndGeneratePrompt(projectId, tempFile, fileName, "image/svg+xml", "SVG附件", false);
                        output += "\n请将使用到的图片放置到资源目录下使用，使用相对路径引用图片。";
                    } catch (Exception e) {
                        log.warn("[FluxService] projectId={} SVG上传失败, url={}", projectId, url, e);
                        output = "";
                    }
                    break;
                default:
                    try {
                        log.info("[FluxService] projectId={} 开始解析其他文件, url={}", projectId, url);
                        String textContent = UrlFile.parseToString(url);
                        File tempFile = writeTextToTempFile(projectId, textContent, fileName);
                        output = uploadFileAndGeneratePrompt(projectId, tempFile, fileName, "application/octet-stream", "文件附件", true);
                    } catch (Exception e) {
                        log.warn("[FluxService] projectId={} 文件处理失败, url={}", projectId, url, e);
                        output = "";
                    }
                    break;
            }
        } catch (Exception e) {
            log.warn("[FluxService] projectId={} 解析文件失败,url={}", projectId, url, e);
            try {
                String textContent = UrlFile.parseToString(url);
                File tempFile = writeTextToTempFile(projectId, textContent, fileName);
                output = uploadFileAndGeneratePrompt(projectId, tempFile, fileName, "application/octet-stream", "文件附件",
                        true);
            } catch (Exception ex) {
                log.warn("[FluxService] projectId={} 解析文件失败,url={}", projectId, url, ex);
                output = "";
            }
        }
        return output;
    }

    private String uploadFileAndGeneratePrompt(Long projectId, File file, String originalFileName,
                                               String contentType, String attachmentType, boolean isTextFile) {
        String uploadFileName = originalFileName;
        try {
            // 根据 isTextFile 参数决定文件名后缀
            if (isTextFile) {
                // 如果标记为文本文件，替换为 .md 后缀
                int lastDotIndex = originalFileName.lastIndexOf('.');
                if (lastDotIndex > 0) {
                    uploadFileName = originalFileName.substring(0, lastDotIndex) + ".md";
                } else {
                    uploadFileName = originalFileName + ".md";
                }
            }

            MultipartFile multipartFile = new FileSystemMultipartFile(file, uploadFileName, contentType);

            log.info("[FluxService] projectId={} 开始上传文件, uploadFileName={}", projectId, uploadFileName);
            Map<String, Object> resp = pageFileBuildClient.uploadAttachmentFile(projectId, multipartFile, uploadFileName);

            if (resp != null) {
                String finalFileName = resp.get("fileName") != null ? String.valueOf(resp.get("fileName"))
                        : uploadFileName;
                String relativePath = resp.get("relativePath") != null ? String.valueOf(resp.get("relativePath"))
                        : ("./attachments/" + finalFileName);
                return String.format("【%s】已上传文件：%s,在项目中的路径是%s。您可以使用此文件进行处理。", attachmentType, finalFileName,
                        relativePath);
            }
            return "";
        } catch (Exception e) {
            log.warn("[FluxService] projectId={} 文件上传失败, uploadFileName={}", projectId, uploadFileName, e);
            return "";
        } finally {
            file.delete();
        }
    }

    private File downloadUrlToTempFile(Long projectId, String url, String fileName) throws IOException {
        log.info("[FluxService] projectId={} 开始下载URL文件, url={}, fileName={}", projectId, url, fileName);
        File tempFile = File.createTempFile("upload_", "_" + fileName);
        String fileUrlWithAk = fileAkUtil.getFileUrlWithAk(url);
        URL fileUrl = new URL(fileUrlWithAk);
        try (InputStream in = fileUrl.openStream();
             OutputStream out = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }
        return tempFile;
    }

    private File writeTextToTempFile(Long projectId, String content, String fileName) throws IOException {
        log.info("[FluxService] projectId={} 开始写入文本到临时文件, content={}, fileName={}", projectId, content, fileName);

        // 提取原始文件名（去掉扩展名）
        String baseFileName = fileName;
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            baseFileName = fileName.substring(0, lastDotIndex);
        }

        // 创建临时文件，使用原始文件名 + .md 扩展名
        File tempFile = File.createTempFile("upload_", "_" + baseFileName + ".md");
        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write(content);
        }
        return tempFile;
    }

    private DataTypeEnum getDocumentTypeFromUrl(String url) {
        String url0 = url;
        url = url.toLowerCase();
        if (url.endsWith(".pdf")) {
            return DataTypeEnum.File_PDF;
        } else if (url.endsWith(".doc") || url.endsWith(".docx")) {
            return DataTypeEnum.File_Doc;
        } else if (url.endsWith(".xls") || url.endsWith(".xlsx")) {
            return DataTypeEnum.File_Excel;
        } else if (url.endsWith(".ppt") || url.endsWith(".pptx")) {
            return DataTypeEnum.File_PPT;
        } else if (url.endsWith(".txt")) {
            return DataTypeEnum.File_Txt;
        } else if (url.endsWith(".text")) {
            return DataTypeEnum.File_Txt;
        } else if (url.endsWith(".json")) {
            return DataTypeEnum.File_Txt;
        } else if (url.endsWith(".html")) {
            return DataTypeEnum.File_Txt;
        } else if (url.endsWith(".htm")) {
            return DataTypeEnum.File_Txt;
        } else if (url.endsWith(".md") || url.endsWith(".markdown") || url.endsWith(".mdown") || url.endsWith(".mkd")) {
            return DataTypeEnum.File_Txt;
        } else if (url.endsWith(".jpg") || url.endsWith(".jpeg")) {
            return DataTypeEnum.File_Image;
        } else if (url.endsWith(".png")) {
            return DataTypeEnum.File_Image;
        } else if (url.endsWith(".gif")) {
            return DataTypeEnum.File_Image;
        } else if (url.endsWith(".bmp")) {
            return DataTypeEnum.File_Image;
        } else if (url.endsWith(".webp")) {
            return DataTypeEnum.File_Image;
        } else if (url.endsWith(".svg")) {
            return DataTypeEnum.File_Svg;
        } else if (url.endsWith(".ico")) {
            return DataTypeEnum.File_Image;
        } else {
            try {
                URL fileUrl = new URL(url0);
                URLConnection connection = fileUrl.openConnection();
                connection.setRequestProperty("User-Agent", "Mozilla/5.0");
                connection.connect();
                String cType = connection.getContentType();
                if (cType != null) {
                    if (cType.contains("pdf")) {
                        return DataTypeEnum.File_PDF;
                    } else if (cType.contains("word")) {
                        return DataTypeEnum.File_Doc;
                    } else if (cType.contains("excel")) {
                        return DataTypeEnum.File_Excel;
                    } else if (cType.contains("ppt")) {
                        return DataTypeEnum.File_PPT;
                    } else if (cType.contains("text")) {
                        return DataTypeEnum.File_Txt;
                    } else if (cType.contains("image")) {
                        return DataTypeEnum.File_Image;
                    }
                }
            } catch (IOException e) {
                // ignore
            }
        }

        return null;
    }

    @Override
    public boolean terminateSession(String sessionId) {
        log.info("[FluxService] 接收到终止会话请求: sessionId={}", sessionId);
        return sessionManager.terminateSession(sessionId);
    }
}
