package com.xspaceagi.custompage.application.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.xspaceagi.custompage.application.service.ICustomPageChatApplicationService;
import com.xspaceagi.custompage.domain.model.CustomPageConversationModel;
import com.xspaceagi.custompage.domain.service.ICustomPageChatDomainService;
import com.xspaceagi.custompage.domain.service.ICustomPageChatFluxService;
import com.xspaceagi.custompage.domain.service.ICustomPageConversationDomainService;
import com.xspaceagi.system.spec.common.UserContext;
import com.xspaceagi.system.spec.dto.ReqResult;
import com.xspaceagi.system.spec.page.SuperPage;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
@Service
public class CustomPageChatApplicationServiceImpl implements ICustomPageChatApplicationService {

    @Resource
    private ICustomPageChatDomainService customPageChatDomainService;
    @Resource
    private ICustomPageChatFluxService customPageChatFluxService;
    @Resource
    private ICustomPageConversationDomainService customPageConversationDomainService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReqResult<Void> saveConversation(CustomPageConversationModel model, UserContext userContext) {
        log.info("[Application] projectId={},保存会话记录", model.getProjectId());

        ReqResult<Long> domainResult = customPageConversationDomainService.saveConversation(model, userContext);

        if (!domainResult.isSuccess()) {
            log.error("[Application] projectId={},保存会话记录失败,error={}", model.getProjectId(),
                    domainResult.getMessage());
            return ReqResult.error(domainResult.getCode(), domainResult.getMessage());
        }

        log.info("[Application] projectId={},保存会话记录成功,result={}", model.getProjectId(), domainResult.getData());
        return ReqResult.success();
    }

    @Override
    public ReqResult<List<CustomPageConversationModel>> listConversations(Long projectId, UserContext userContext) {
        log.info("[Application] projectId={},查询用户会话记录", projectId);

        List<CustomPageConversationModel> modelList = new ArrayList<>();
        List<CustomPageConversationModel> models = customPageConversationDomainService
                .listByProjectId(projectId, userContext.getUserId());
        if (models != null && !models.isEmpty()) {
            modelList.addAll(models);
        }

        log.info("[Application] projectId={},查询用户会话记录返回size={}", projectId, modelList.size());
        return ReqResult.success(modelList);
    }

    @Override
    public ReqResult<SuperPage<CustomPageConversationModel>> pageQueryConversations(
            CustomPageConversationModel queryModel, Long current, Long pageSize, UserContext userContext) {
        log.info("[Application] projectId={},分页查询用户会话记录, current={}, pageSize={}", queryModel.getProjectId(), current,
                pageSize);

        ReqResult<SuperPage<CustomPageConversationModel>> domainResult = customPageConversationDomainService
                .pageQuery(queryModel, current, pageSize, userContext);

        if (!domainResult.isSuccess()) {
            log.error("[Application] projectId={},分页查询用户会话记录失败,error={}", queryModel.getProjectId(),
                    domainResult.getMessage());
            return ReqResult.error(domainResult.getCode(), domainResult.getMessage());
        }

        log.info("[Application] projectId={},分页查询用户会话记录成功,total={}", queryModel.getProjectId(),
                domainResult.getData().getTotal());
        return domainResult;
    }

    @Override
    public Flux<Map<String, Object>> sendAgentChatFlux(Map<String, Object> chatBody,
            UserContext userContext) {
        log.info("[Application] 发送聊天消息（Flux响应式）,chatBody keys={}", chatBody == null ? null : chatBody.keySet());
        Optional.ofNullable(chatBody).orElseThrow(() -> new IllegalArgumentException("请求体不能为空"));

        return customPageChatFluxService.sendAgentChatFlux(chatBody, userContext);
    }

    @Override
    public ReqResult<Void> terminateChatSession(String sessionId, UserContext userContext) {
        log.info("[Application] 接收到终止会话请求: sessionId={}", sessionId);
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return ReqResult.error("0001", "sessionId不能为空");
        }

        customPageChatFluxService.terminateSession(sessionId);
        return ReqResult.success();
    }

    @Override
    public SseEmitter startAgentSessionSse(String sessionId, UserContext userContext) {
        log.info("[Application] 建立会话SSE,sessionId={}", sessionId);
        return customPageChatDomainService.startAgentSessionSse(sessionId, userContext);
    }

    @Override
    public ReqResult<Map<String, Object>> agentSessionCancel(String projectId, String sessionId,
            UserContext userContext) {
        log.info("[Application] projectId={},取消agent任务,sessionId={}", projectId, sessionId);
        return customPageChatDomainService.agentSessionCancel(projectId, sessionId, userContext);
    }

    @Override
    public ReqResult<Map<String, Object>> getAgentStatus(String projectId, UserContext userContext) {
        log.info("[Application] projectId={},查询Agent状态", projectId);
        return customPageChatDomainService.getAgentStatus(projectId, userContext);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReqResult<Map<String, Object>> stopAgent(String projectId, UserContext userContext) {
        log.info("[Application] projectId={},停止Agent服务", projectId);
        return customPageChatDomainService.stopAgent(projectId, userContext);
    }
}
