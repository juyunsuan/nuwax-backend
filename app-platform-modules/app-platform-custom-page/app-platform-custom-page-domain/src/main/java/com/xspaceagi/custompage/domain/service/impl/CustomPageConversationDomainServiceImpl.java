package com.xspaceagi.custompage.domain.service.impl;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.xspaceagi.custompage.domain.model.CustomPageConversationModel;
import com.xspaceagi.custompage.domain.repository.ICustomPageConfigRepository;
import com.xspaceagi.custompage.domain.repository.ICustomPageConversationRepository;
import com.xspaceagi.custompage.domain.service.ICustomPageConversationDomainService;
import com.xspaceagi.system.sdk.permission.SpacePermissionService;
import com.xspaceagi.system.spec.common.UserContext;
import com.xspaceagi.system.spec.dto.ReqResult;
import com.xspaceagi.system.spec.page.SuperPage;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CustomPageConversationDomainServiceImpl implements ICustomPageConversationDomainService {

    @Resource
    private ICustomPageConversationRepository customPageConversationRepository;
    @Resource
    private ICustomPageConfigRepository customPageConfigRepository;
    @Resource
    private SpacePermissionService spacePermissionService;

    @Override
    public ReqResult<Long> saveConversation(CustomPageConversationModel model, UserContext userContext) {
        log.info("[Domain] 保存用户会话记录, projectId={}, topic={}", model.getProjectId(), model.getTopic());

        Optional.ofNullable(model.getProjectId()).filter(x -> x > 0)
                .orElseThrow(() -> new IllegalArgumentException("projectId不能为空或无效"));
        Optional.ofNullable(model.getContent()).filter(x -> !x.trim().isEmpty())
                .orElseThrow(() -> new IllegalArgumentException("content不能为空"));

        try {
            var configModel = customPageConfigRepository.getById(model.getProjectId());
            if (configModel == null) {
                log.warn("[Domain] 项目不存在, projectId={}", model.getProjectId());
                return ReqResult.error("0001", "项目不存在");
            }

            // 校验空间权限
            spacePermissionService.checkSpaceUserPermission(configModel.getSpaceId());

            // 设置 spaceId
            model.setSpaceId(configModel.getSpaceId());

            Long id = customPageConversationRepository.save(model, userContext);
            log.info("[Domain] 保存用户会话记录成功, id={}", id);

            return ReqResult.success(id);
        } catch (Exception e) {
            log.error("[Domain] 保存用户会话记录异常, projectId={}", model.getProjectId(), e);
            return ReqResult.error("0001", "保存用户会话记录异常: " + e.getMessage());
        }
    }

    @Override
    public List<CustomPageConversationModel> listByProjectId(Long projectId, Long userId) {
        log.info("[Domain] 查询项目会话记录列表, projectId={}, userId={}", projectId, userId);

        List<CustomPageConversationModel> models = customPageConversationRepository
                .listByProjectId(projectId, userId);
        if (models != null && !models.isEmpty()) {
            // 校验空间权限
            spacePermissionService.checkSpaceUserPermission(models.get(0).getSpaceId());
        }

        return models;
    }

    @Override
    public ReqResult<SuperPage<CustomPageConversationModel>> pageQuery(CustomPageConversationModel queryModel,
            Long current, Long pageSize, UserContext userContext) {
        log.info("[Domain] 分页查询会话记录, projectId={}, current={}, pageSize={}", queryModel.getProjectId(), current,
                pageSize);

        try {
            Optional.ofNullable(queryModel.getProjectId()).filter(x -> x > 0)
                    .orElseThrow(() -> new IllegalArgumentException("projectId不能为空或无效"));
            Optional.ofNullable(current).filter(x -> x > 0)
                    .orElseThrow(() -> new IllegalArgumentException("current不能为空或无效"));
            Optional.ofNullable(pageSize).filter(x -> x > 0)
                    .orElseThrow(() -> new IllegalArgumentException("pageSize不能为空或无效"));

            // 校验项目是否存在并获取空间权限
            var configModel = customPageConfigRepository.getById(queryModel.getProjectId());
            if (configModel == null) {
                log.warn("[Domain] 项目不存在, projectId={}", queryModel.getProjectId());
                return ReqResult.error("0001", "项目不存在");
            }

            // 校验空间权限
            spacePermissionService.checkSpaceUserPermission(configModel.getSpaceId());

            SuperPage<CustomPageConversationModel> result = customPageConversationRepository.pageQuery(queryModel,
                    current, pageSize);
            log.info("[Domain] 分页查询会话记录成功, total={}", result.getTotal());

            return ReqResult.success(result);
        } catch (Exception e) {
            log.error("[Domain] 分页查询会话记录异常, projectId={}", queryModel.getProjectId(), e);
            return ReqResult.error("0001", "分页查询会话记录异常: " + e.getMessage());
        }
    }

}
