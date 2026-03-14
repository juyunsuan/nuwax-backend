package com.xspaceagi.custompage.ui.web.controller;

import com.alibaba.fastjson2.JSON;
import com.xspaceagi.agent.core.adapter.application.ModelApplicationService;
import com.xspaceagi.agent.core.adapter.dto.ModelQueryDto;
import com.xspaceagi.agent.core.adapter.dto.config.ModelConfigDto;
import com.xspaceagi.agent.core.spec.enums.ModelApiProtocolEnum;
import com.xspaceagi.agent.core.spec.enums.ModelTypeEnum;
import com.xspaceagi.custompage.application.service.ICustomPageChatApplicationService;
import com.xspaceagi.custompage.application.service.ICustomPageConfigApplicationService;
import com.xspaceagi.custompage.domain.model.CustomPageConfigModel;
import com.xspaceagi.custompage.domain.model.CustomPageConversationModel;
import com.xspaceagi.custompage.ui.web.config.SseConfig;
import com.xspaceagi.custompage.ui.web.dto.ConversationPageQueryReq;
import com.xspaceagi.custompage.ui.web.dto.ConversationRes;
import com.xspaceagi.custompage.ui.web.dto.CustomPageModelRes;
import com.xspaceagi.custompage.ui.web.dto.SaveConversationReq;
import com.xspaceagi.system.sdk.permission.SpacePermissionService;
import com.xspaceagi.system.sdk.server.IUserDataPermissionRpcService;
import com.xspaceagi.system.sdk.server.IUserMetricRpcService;
import com.xspaceagi.system.sdk.service.dto.BizType;
import com.xspaceagi.system.sdk.service.dto.PeriodType;
import com.xspaceagi.system.sdk.service.dto.UserDataPermissionDto;
import com.xspaceagi.system.spec.annotation.RequireResource;
import com.xspaceagi.system.spec.common.RequestContext;
import com.xspaceagi.system.spec.common.UserContext;
import com.xspaceagi.system.spec.dto.ReqResult;
import com.xspaceagi.system.spec.enums.YesOrNoEnum;
import com.xspaceagi.system.spec.exception.SpacePermissionException;
import com.xspaceagi.system.spec.page.PageQueryVo;
import com.xspaceagi.system.spec.page.SuperPage;
import com.xspaceagi.system.spec.tenant.thread.TenantRunnable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import static com.xspaceagi.system.spec.enums.ResourceEnum.PAGE_APP_AI_CHAT;
import static com.xspaceagi.system.spec.enums.ResourceEnum.PAGE_APP_QUERY_DETAIL;

@Tag(name = "网页应用", description = "网页应用相关接口")
@RestController
@RequestMapping("/api/custom-page")
@Slf4j
@RequiredArgsConstructor
public class CustomPageChatController extends BaseController {

    @Resource
    private ModelApplicationService modelApplicationService;
    @Resource
    private SpacePermissionService spacePermissionService;
    @Resource
    private SseConfig.SseConnectionManager sseConnectionManager;
    @Resource
    private ICustomPageChatApplicationService customPageChatApplicationService;
    @Resource
    private ICustomPageConfigApplicationService customPageConfigApplicationService;
    @Resource
    private IUserDataPermissionRpcService userDataPermissionRpcService;
    @Resource
    private IUserMetricRpcService iUserMetricRpcService;
    @Resource
    @Qualifier("aiChatFluxExecutor")
    private Executor aiChatFluxExecutor;

    @RequireResource(PAGE_APP_AI_CHAT)
    @Operation(summary = "发送聊天消息（流式输出）", description = "使用响应式流处理AI聊天，通过SSE推送执行进度")
    @PostMapping(value = "/ai-chat-flux", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter aiChatFlux(@RequestBody Map<String, Object> chatBody, HttpServletResponse response) {
        log.info("[Web] 接收到聊天消息Flux响应式流请求");

        // 设置SSE相关的响应头
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("Content-Type", "text/event-stream;charset=UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Headers",
                "Origin, X-Requested-With, Content-Type, Accept, Authorization, Cache-Control");
        response.setHeader("Access-Control-Allow-Methods", "HEAD,GET,POST,PUT,DELETE,OPTIONS");
        response.setHeader("Access-Control-Allow-Credentials", "true");

        try {
            if (chatBody == null) {
                SseEmitter emitter = new SseEmitter();
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data("{\"code\":\"0001\",\"message\":\"请求体不能为空\"}"));
                emitter.complete();
                return emitter;
            }

            UserContext userContext = getUser();

            // 检查会话次数
            UserDataPermissionDto userDataPermission = userDataPermissionRpcService.getUserDataPermission(userContext.getUserId());
            try {
                BigDecimal ct = iUserMetricRpcService.queryMetricCurrent(userContext.getTenantId(), userContext.getUserId(), BizType.APP_DEV_CHAT.getCode(), PeriodType.DAY);
                if (userDataPermission.getPageDailyPromptLimit() != null && userDataPermission.getPageDailyPromptLimit() >= 0 && ct.intValue() >= userDataPermission.getPageDailyPromptLimit()) {
                    String msg = "网页应用开发每日限额已用完，你当前每日开发对话的上限次数为" + userDataPermission.getPageDailyPromptLimit();
                    SseEmitter emitter = new SseEmitter();
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("{\"code\":\"0001\",\"message\":\"" + msg + "\"}"));
                    emitter.complete();
                    return emitter;
                }
            } catch (Exception e) {
                log.error("queryMetricCurrent error", e);
            }


            SseEmitter emitter = new SseEmitter(10L * 60 * 1000);

            // sendAgentChatFlux() 只是创建了一个 Flux 对象，不会立即执行
            Flux<Map<String, Object>> flux = customPageChatApplicationService.sendAgentChatFlux(chatBody, userContext);

            //只有调用 subscribe() 时，才会在调用线程中同步执行 Flux.create() 的回调
            // 在异步线程中订阅 Flux 并发送事件
            // 使用 TenantRunnable 在异步线程中传递 RequestContext
            aiChatFluxExecutor.execute(new TenantRunnable(() -> {
                flux.subscribe(
                        data -> {
                            try {
                                String eventType = (String) data.get("type");
                                String jsonData = JSON.toJSONString(data);
                                emitter.send(SseEmitter.event()
                                        .name(eventType)
                                        .data(jsonData));
                                log.debug("[Web] Flux 发送事件: {}", eventType);
                            } catch (Exception e) {
                                log.error("[Web] Flux 发送事件失败", e);
                            }
                        },
                        error -> {
                            log.error("[Web] Flux 流异常", error);
                            try {
                                Map<String, Object> errorData = new HashMap<>();
                                errorData.put("type", "error");
                                errorData.put("code", "0001");
                                errorData.put("message", error.getMessage());
                                emitter.send(SseEmitter.event()
                                        .name("error")
                                        .data(JSON.toJSONString(errorData)));
                                emitter.complete();
                            } catch (Exception e) {
                                emitter.completeWithError(e);
                            }
                        },
                        () -> {
                            log.info("[Web] Flux 流完成");
                            emitter.complete();
                        });
            }));

            return emitter;
        } catch (Exception e) {
            log.error("[Web] 发送聊天消息Flux流异常", e);
            SseEmitter emitter = new SseEmitter();
            try {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data("{\"code\":\"0001\",\"message\":\"发送聊天消息异常: " + e.getMessage() + "\"}"));
                emitter.complete();
            } catch (Exception ex) {
                log.error("[Web] 发送错误消息失败", ex);
                emitter.completeWithError(ex);
            }
            return emitter;
        }
    }

    @RequireResource(PAGE_APP_AI_CHAT)
    @Operation(summary = "终止聊天SSE会话", description = "通过会话ID终止正在进行的聊天SSE会话")
    @PostMapping(value = "/ai-chat-terminate", produces = MediaType.APPLICATION_JSON_VALUE)
    public ReqResult<Void> aiChatTerminate(@RequestBody Map<String, Object> requestBody) {
        log.info("[Web] 接收到终止聊天SSE会话请求");
        try {
            if (requestBody == null) {
                return ReqResult.error("0001", "请求体不能为空");
            }
            Object sessionIdObj = requestBody.get("session_id");
            if (sessionIdObj == null) {
                return ReqResult.error("0001", "session_id不能为空");
            }

            String sessionId = String.valueOf(sessionIdObj);
            UserContext userContext = getUser();
            return customPageChatApplicationService.terminateChatSession(sessionId, userContext);
        } catch (Exception e) {
            log.error("[Web] 终止聊天SSE会话异常", e);
            return ReqResult.error("0001", "终止聊天SSE会话异常: " + e.getMessage());
        }
    }

    @RequireResource(PAGE_APP_AI_CHAT)
    @Operation(summary = "建立Agent会话通知连接", description = "通过SSE建立与指定会话的实时通信")
    @GetMapping(value = "/ai-session-sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter startSessionSse(@RequestParam("session_id") String sessionId,
                                      HttpServletResponse response) {
        log.info("[Web] 建立会话SSE，sessionId={}", sessionId);

        // 设置SSE相关的响应头
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("Content-Type", "text/event-stream;charset=UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Headers",
                "Origin, X-Requested-With, Content-Type, Accept, Authorization, Cache-Control");
        response.setHeader("Access-Control-Allow-Methods", "HEAD,GET,POST,PUT,DELETE,OPTIONS");
        response.setHeader("Access-Control-Allow-Credentials", "true");

        UserContext userContext = getUser();
        SseEmitter emitter = customPageChatApplicationService.startAgentSessionSse(sessionId, userContext);

        sseConnectionManager.addConnection(sessionId, emitter);
        Long tenantId = RequestContext.get().getTenantId();
        emitter.onCompletion(() -> {
            //会话数增加1
            iUserMetricRpcService.incrementMetricAllPeriods(tenantId, userContext.getUserId(), BizType.APP_DEV_CHAT.getCode(), BigDecimal.ONE);
        });
        return emitter;
    }

    @RequireResource(PAGE_APP_AI_CHAT)
    @Operation(summary = "取消Agent任务", description = "取消指定会话的Agent任务")
    @PostMapping(value = "/ai-session-cancel", produces = MediaType.APPLICATION_JSON_VALUE)
    public ReqResult<Map<String, Object>> agentSessionCancel(@RequestBody Map<String, Object> requestBody) {
        log.info("[Web] 接收到取消Agent任务请求");
        try {
            if (requestBody == null) {
                return ReqResult.error("0001", "请求体不能为空");
            }

            Object projectIdObj = requestBody.get("project_id");
            Object sessionIdObj = requestBody.get("session_id");

            if (projectIdObj == null) {
                return ReqResult.error("0001", "project_id为必填参数");
            }

            String projectId = String.valueOf(projectIdObj);
            String sessionId = sessionIdObj == null ? null : String.valueOf(sessionIdObj);

            UserContext userContext = getUser();
            return customPageChatApplicationService.agentSessionCancel(projectId, sessionId, userContext);
        } catch (Exception e) {
            log.error("[Web] 取消Agent任务异常", e);
            return ReqResult.error("0001", "取消Agent任务异常: " + e.getMessage());
        }
    }

    @RequireResource(PAGE_APP_AI_CHAT)
    @Operation(summary = "查询Agent状态", description = "查询指定项目的Agent服务状态信息")
    @GetMapping(value = "/agent/status/{project_id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ReqResult<Map<String, Object>> getAgentStatus(@PathVariable("project_id") String projectId) {
        log.info("[Web] 接收到查询Agent状态请求，projectId={}", projectId);
        try {
            if (projectId == null || projectId.trim().isEmpty()) {
                return ReqResult.error("0001", "project_id不能为空");
            }

            UserContext userContext = getUser();
            return customPageChatApplicationService.getAgentStatus(projectId, userContext);
        } catch (Exception e) {
            log.error("[Web] 查询Agent状态异常，projectId={}", projectId, e);
            return ReqResult.error("0001", "查询Agent状态异常: " + e.getMessage());
        }
    }

    @RequireResource(PAGE_APP_AI_CHAT)
    @Operation(summary = "停止Agent服务", description = "停止指定项目的Agent服务")
    @PostMapping(value = "/agent/stop", produces = MediaType.APPLICATION_JSON_VALUE)
    public ReqResult<Map<String, Object>> stopAgent(@RequestParam("project_id") String projectId) {
        log.info("[Web] 接收到停止Agent服务请求，projectId={}", projectId);
        try {
            if (projectId == null || projectId.trim().isEmpty()) {
                return ReqResult.error("0001", "project_id不能为空");
            }

            UserContext userContext = getUser();
            return customPageChatApplicationService.stopAgent(projectId, userContext);
        } catch (Exception e) {
            log.error("[Web] 停止Agent服务异常，projectId={}", projectId, e);
            return ReqResult.error("0001", "停止Agent服务异常: " + e.getMessage());
        }
    }

    @RequireResource(PAGE_APP_QUERY_DETAIL)
    @Operation(summary = "查询模型列表", description = "查询可用的对话模型列表和多模态模型列表")
    @GetMapping(value = "/list-models", produces = MediaType.APPLICATION_JSON_VALUE)
    public ReqResult<CustomPageModelRes> listModels(@RequestParam("projectId") Long projectId) {
        log.info("[Web] 接收到查询模型列表请求");
        try {
            if (projectId == null || projectId <= 0) {
                return ReqResult.error("0001", "project_id不能为空");
            }
            CustomPageConfigModel configModel = customPageConfigApplicationService.getByProjectId(projectId);
            if (configModel == null) {
                return ReqResult.error("0001", "项目不存在");
            }

            spacePermissionService.checkSpaceUserPermission(configModel.getSpaceId());

            List<CustomPageModelRes.ModelDto> chatModelList = new ArrayList<>();
            List<CustomPageModelRes.ModelDto> multiModelList = new ArrayList<>();

            ModelQueryDto queryDto = new ModelQueryDto();
            queryDto.setSpaceId(configModel.getSpaceId());
            // queryDto.setModelType(ModelTypeEnum.Chat);
            // queryDto.setApiProtocol(ModelApiProtocolEnum.Anthropic);
            // queryDto.setScope(null);
            List<ModelConfigDto> modelList = modelApplicationService.queryModelConfigList(queryDto);
            if (modelList != null && modelList.size() > 0) {
                modelList.stream()
                        .filter(m -> (m.getEnabled() == null || YesOrNoEnum.Y.getKey().equals(m.getEnabled())))
                        .forEach(m -> {
                            if (m.getType() == ModelTypeEnum.Chat) {
                                if (m.getApiProtocol() == ModelApiProtocolEnum.Anthropic) {
                                    chatModelList.add(convertModelDto(m));
                                }
                            } else if (m.getType() == ModelTypeEnum.Multi) {
                                multiModelList.add(convertModelDto(m));
                            }
                        });
            }
            log.info("[Web] 查询到 {} 个对话模型，{} 个多模态模型", chatModelList.size(), multiModelList.size());

            CustomPageModelRes res = new CustomPageModelRes();
            res.setChatModelList(chatModelList);
            res.setMultiModelList(multiModelList);
            return ReqResult.success(res);
        } catch (Exception e) {
            log.error("[Web] 查询模型列表异常", e);
            return ReqResult.error("0001", "查询模型列表异常: " + e.getMessage());
        }
    }

    private CustomPageModelRes.ModelDto convertModelDto(ModelConfigDto m) {
        CustomPageModelRes.ModelDto dto = new CustomPageModelRes.ModelDto();
        dto.setId(m.getId());
        dto.setName(m.getName());
        dto.setDescription(m.getDescription());
        dto.setModel(m.getModel());
        dto.setApiProtocol(m.getApiProtocol());
        dto.setTenantId(m.getTenantId());
        dto.setSpaceId(m.getSpaceId());
        dto.setIsReasonModel(m.getIsReasonModel());
        dto.setMaxTokens(m.getMaxTokens());
        return dto;
    }

    @RequireResource(PAGE_APP_AI_CHAT)
    @Operation(summary = "保存用户会话记录", description = "保存用户会话记录")
    @PostMapping(value = "/save-conversation", produces = MediaType.APPLICATION_JSON_VALUE)
    public ReqResult<Void> saveConversation(@RequestBody SaveConversationReq req) {
        log.info("[Web] 接收到保存会话记录请求, projectId={}", req.getProjectId());
        try {
            CustomPageConversationModel model = new CustomPageConversationModel();
            model.setProjectId(req.getProjectId());
            model.setTopic(req.getTopic());
            model.setContent(req.getContent());

            UserContext userContext = getUser();
            return customPageChatApplicationService.saveConversation(model, userContext);
        } catch (SpacePermissionException e) {
            log.error("[Web] 保存会话记录失败，projectId={}, {}", req.getProjectId(), e.getMessage());
            return ReqResult.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("[Web] 保存会话记录失败，projectId={}", req.getProjectId(), e);
            return ReqResult.error("0001", "保存会话记录失败: " + e.getMessage());
        }
    }

    @RequireResource(PAGE_APP_QUERY_DETAIL)
    @Operation(summary = "查询会话记录", description = "查询会话记录")
    @GetMapping(value = "/list-conversations", produces = MediaType.APPLICATION_JSON_VALUE)
    public ReqResult<List<ConversationRes>> listConversations(@RequestParam("projectId") Long projectId) {
        log.info("[Web] 接收到查询会话记录请求, projectId={}", projectId);
        try {
            if (projectId == null || projectId <= 0) {
                return ReqResult.error("0001", "projectId不能为空或无效");
            }

            UserContext userContext = getUser();
            ReqResult<List<CustomPageConversationModel>> result = customPageChatApplicationService
                    .listConversations(projectId, userContext);
            if (!result.isSuccess()) {
                return ReqResult.error(result.getCode(), result.getMessage());
            }
            return ReqResult.success(result.getData().stream()
                    .map(m -> {
                        ConversationRes res = new ConversationRes();
                        res.setProjectId(m.getProjectId());
                        res.setConversationId(m.getId());
                        res.setTopic(m.getTopic());
                        res.setContent(m.getContent());
                        res.setCreated(m.getCreated());
                        res.setCreatorId(m.getCreatorId());
                        return res;
                    })
                    .collect(Collectors.toList()));
        } catch (SpacePermissionException e) {
            log.error("[Web] 查询会话记录失败，projectId={}, {}", projectId, e.getMessage());
            return ReqResult.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("[Web] 查询会话记录失败，projectId={}", projectId, e);
            return ReqResult.error("0001", "查询会话记录失败: " + e.getMessage());
        }
    }

    @RequireResource(PAGE_APP_QUERY_DETAIL)
    @Operation(summary = "分页查询会话记录", description = "分页查询会话记录")
    @PostMapping(value = "/page-query-conversations", produces = MediaType.APPLICATION_JSON_VALUE)
    public ReqResult<SuperPage<ConversationRes>> pageQueryConversations(
            @RequestBody PageQueryVo<ConversationPageQueryReq> pageQueryVo) {
        log.info("[Web] 接收到分页查询会话记录请求, pageQueryVo={}", pageQueryVo);
        try {
            if (pageQueryVo == null || pageQueryVo.getQueryFilter() == null) {
                return ReqResult.error("0001", "请求参数不能为空");
            }

            ConversationPageQueryReq queryReq = pageQueryVo.getQueryFilter();
            if (queryReq.getProjectId() == null || queryReq.getProjectId() <= 0) {
                return ReqResult.error("0001", "projectId不能为空或无效");
            }

            CustomPageConversationModel queryModel = new CustomPageConversationModel();
            queryModel.setProjectId(queryReq.getProjectId());

            UserContext userContext = getUser();
            ReqResult<SuperPage<CustomPageConversationModel>> result = customPageChatApplicationService
                    .pageQueryConversations(queryModel, pageQueryVo.getCurrent(), pageQueryVo.getPageSize(),
                            userContext);

            if (!result.isSuccess()) {
                return ReqResult.error(result.getCode(), result.getMessage());
            }

            if (result.getData() == null) {
                return ReqResult.error("0001", "查询结果为空");
            }

            SuperPage<ConversationRes> responsePage = new SuperPage<>(result.getData().getCurrent(),
                    result.getData().getSize(), result.getData().getTotal());

            List<ConversationRes> conversationResList = new ArrayList<>();
            if (result.getData().getRecords() != null && !result.getData().getRecords().isEmpty()) {
                conversationResList = result.getData().getRecords().stream()
                        .map(m -> {
                            ConversationRes res = new ConversationRes();
                            res.setProjectId(m.getProjectId());
                            res.setConversationId(m.getId());
                            res.setTopic(m.getTopic());
                            res.setContent(m.getContent());
                            res.setCreated(m.getCreated());
                            res.setCreatorId(m.getCreatorId());
                            return res;
                        })
                        .collect(Collectors.toList());
            }

            responsePage.setRecords(conversationResList);

            log.info("[Web] 分页查询会话记录成功, total={}", responsePage.getTotal());
            return ReqResult.success(responsePage);
        } catch (SpacePermissionException e) {
            log.error("[Web] 分页查询会话记录失败", e);
            return ReqResult.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("[Web] 分页查询会话记录失败", e);
            return ReqResult.error("0001", "分页查询会话记录失败: " + e.getMessage());
        }
    }
}