package com.xspaceagi.custompage.application.service.impl;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xspaceagi.custompage.application.service.ICustomPageBuildApplicationService;
import com.xspaceagi.custompage.domain.model.CustomPageBuildModel;
import com.xspaceagi.custompage.domain.service.ICustomPageBuildDomainService;
import com.xspaceagi.system.spec.common.UserContext;
import com.xspaceagi.system.spec.dto.ReqResult;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CustomPageBuildApplicationServiceImpl implements ICustomPageBuildApplicationService {

    @Resource
    private ICustomPageBuildDomainService customPageBuildDomainService;

    // 不需要事务
    // @Transactional(rollbackFor = Exception.class, timeout = 30)
    @Override
    public ReqResult<Map<String, Object>> startDev(Long projectId, UserContext userContext) {
        log.info("[Application] projectId={},启动dev服务器", projectId);

        ReqResult<Map<String, Object>> result = customPageBuildDomainService.startDev(projectId, userContext);

        log.info("[Application] projectId={},启动dev器响应,result={}", projectId, result);
        return result;
    }

    // 需要开启事务
    @Transactional(rollbackFor = Exception.class, timeout = 60)
    @Override
    public ReqResult<Map<String, Object>> build(Long projectId, String publishType, UserContext userContext) {
        log.info("[Application] projectId={},构建并发布前端项目", projectId);

        ReqResult<Map<String, Object>> result = customPageBuildDomainService.build(projectId, publishType, userContext);

        log.info("[Application] projectId={},构建并发布前端项目响应,result={}", projectId, result);
        return result;
    }

    // 不需要事务
    // @Transactional(rollbackFor = Exception.class, timeout = 30)
    @Override
    public ReqResult<Map<String, Object>> stopDev(Long projectId, UserContext userContext) {
        log.info("[Application] projectId={},停止dev服务器", projectId);

        ReqResult<Map<String, Object>> result = customPageBuildDomainService.stopDev(projectId, userContext);

        log.info("[Application] projectId={},停止dev服务器响应,result={}", projectId, result);
        return result;
    }

    // 不需要事务
    // @Transactional(rollbackFor = Exception.class, timeout = 30)
    @Override
    public ReqResult<Map<String, Object>> restartDev(Long projectId, UserContext userContext) {
        log.info("[Application] projectId={},重启dev服务器", projectId);

        ReqResult<Map<String, Object>> result = customPageBuildDomainService.restartDev(projectId, userContext);

        log.info("[Application] projectId={},重启dev服务器响应,result={}", projectId, result);
        return result;
    }

    // 不需要事务
    // @Transactional(rollbackFor = Exception.class, timeout = 10)
    @Override
    public ReqResult<Map<String, Object>> keepAlive(Long projectId, UserContext userContext) {
        log.debug("[keepAlive] projectId={},保活处理", projectId);

        ReqResult<Map<String, Object>> result = customPageBuildDomainService.keepAlive(projectId, userContext);

        log.info("[keepAlive] projectId={},保活处理响应,result={}", projectId, result);
        return result;
    }

    @Override
    public CustomPageBuildModel getByProjectId(Long id) {
        return customPageBuildDomainService.getByProjectId(id);
    }

    @Override
    public ReqResult<Map<String, Object>> initProject(Long projectId, UserContext userContext) {
        log.info("[initProject] projectId={},开始初始化项目工程", projectId);

        ReqResult<Map<String, Object>> result = customPageBuildDomainService.initProject(projectId);

        log.info("[initProject] projectId={},开始初始化项目响应,result={}", projectId, result);
        return result;
    }

    @Override
    public ReqResult<Map<String, Object>> deleteProjectFiles(CustomPageBuildModel model, UserContext userContext) {
        log.info("[deleteProjectFiles] projectId={},开始删除项目文件", model.getProjectId());

        ReqResult<Map<String, Object>> result = customPageBuildDomainService.deleteProjectFiles(model, userContext);

        log.info("[deleteProjectFiles] projectId={},删除项目文件响应,result={}", model.getProjectId(), result);
        return result;
    }

    @Override
    public ReqResult<Map<String, Object>> getDevLog(Long projectId, Integer startIndex, String logType, UserContext userContext) {
        log.debug("[getDevLog] projectId={}, startIndex={}, 开始查询日志", projectId, startIndex);

        ReqResult<Map<String, Object>> result = customPageBuildDomainService.getDevLog(projectId, startIndex, logType, userContext);

        log.debug("[getDevLog] projectId={}, 查询日志响应, code={}", projectId, result.getCode());
        return result;
    }

    @Override
    public ReqResult<Map<String, Object>> copyProject(Long sourceProjectId, Long targetProjectId, UserContext userContext) {
        log.info("[copyProject] sourceProjectId={},targetProjectId={},开始复制项目工程", sourceProjectId, targetProjectId);

        ReqResult<Map<String, Object>> result = customPageBuildDomainService.copyProject(sourceProjectId, targetProjectId);

        log.info("[copyProject] sourceProjectId={},targetProjectId={},复制项目工程响应,result={}", sourceProjectId, targetProjectId, result);
        return result;
    }

    @Override
    public ReqResult<Map<String, Object>> getLogCacheStats() {
        log.info("[Application] 获取日志缓存统计");

        ReqResult<Map<String, Object>> result = customPageBuildDomainService.getLogCacheStats();

        log.info("[Application] 获取日志缓存统计响应, result={}", result);
        return result;
    }

    @Override
    public ReqResult<Map<String, Object>> clearAllLogCache() {
        log.info("[Application] 清理所有日志缓存");

        ReqResult<Map<String, Object>> result = customPageBuildDomainService.clearAllLogCache();

        log.info("[Application] 清理所有日志缓存响应, result={}", result);
        return result;
    }
}