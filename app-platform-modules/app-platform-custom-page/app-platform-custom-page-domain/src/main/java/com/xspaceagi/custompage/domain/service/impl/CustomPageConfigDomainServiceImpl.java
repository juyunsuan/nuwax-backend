package com.xspaceagi.custompage.domain.service.impl;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.xspaceagi.custompage.sdk.dto.*;
import com.xspaceagi.system.spec.common.UserContext;
import com.xspaceagi.system.spec.dto.ReqResult;
import com.xspaceagi.system.spec.enums.YesOrNoEnum;
import com.xspaceagi.system.spec.enums.YnEnum;
import com.xspaceagi.system.spec.exception.BizException;
import com.xspaceagi.system.spec.id.IdGenerator;
import com.xspaceagi.system.spec.page.SuperPage;
import com.xspaceagi.system.spec.utils.IdGeneratorRetryUtil;
import com.xspaceagi.system.spec.utils.RedisUtil;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson2.JSON;
import com.xspaceagi.agent.core.sdk.IAgentRpcService;
import com.xspaceagi.agent.core.sdk.dto.PluginEnableOrUpdateDto;
import com.xspaceagi.agent.core.sdk.dto.PluginInfoDto;
import com.xspaceagi.agent.core.sdk.dto.TemplateEnableOrUpdateDto;
import com.xspaceagi.agent.core.sdk.dto.WorkflowInfoDto;
import com.xspaceagi.agent.core.sdk.enums.TargetTypeEnum;
import com.xspaceagi.custompage.domain.dto.PageFileInfo;
import com.xspaceagi.custompage.domain.gateway.PageFileBuildClient;
import com.xspaceagi.custompage.domain.keepalive.IKeepAliveService;
import com.xspaceagi.custompage.domain.model.CustomPageBuildModel;
import com.xspaceagi.custompage.domain.model.CustomPageConfigModel;
import com.xspaceagi.custompage.domain.proxypath.ICustomPageProxyPathService;
import com.xspaceagi.custompage.domain.repository.ICustomPageBuildRepository;
import com.xspaceagi.custompage.domain.repository.ICustomPageConfigRepository;
import com.xspaceagi.custompage.domain.service.ICustomPageConfigDomainService;
import com.xspaceagi.system.sdk.permission.SpacePermissionService;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CustomPageConfigDomainServiceImpl implements ICustomPageConfigDomainService {

    @Resource
    private RedisUtil redisUtil;
    @Resource
    private IdGenerator idGenerator;
    @Resource
    private IAgentRpcService agentRpcService;
    @Resource
    private IKeepAliveService keepAliveService;
    @Resource
    private PageFileBuildClient pageFileBuildClient;
    @Resource
    private SpacePermissionService spacePermissionService;
    @Resource
    private ICustomPageConfigRepository customPageConfigRepository;
    @Resource
    private ICustomPageBuildRepository customPageBuildRepository;
    @Resource
    private ICustomPageProxyPathService customPageProxyPathService;

    @Override
    public ReqResult<CustomPageConfigModel> create(CustomPageConfigModel model, UserContext userContext) {
        // 校验空间权限
        spacePermissionService.checkSpaceUserPermission(model.getSpaceId());

        model.setBuildRunning(YesOrNoEnum.N.getKey());
        model.setTenantId(userContext.getTenantId());
        model.setCreatorId(userContext.getUserId());
        model.setCreatorName(userContext.getUserName());
        model.setYn(YnEnum.Y.getKey());

        // 使用重试工具，自动处理ID重复的情况
        CustomPageConfigModel result = IdGeneratorRetryUtil.executeWithRetry(
                idGenerator,
                16,
                (projectId) -> {
                    model.setId(projectId);
                    model.setBasePath("/" + projectId);
                    Long id = customPageConfigRepository.add(model, userContext);
                    model.setId(id);
                    return model;
                },
                "创建用户页面",
                3 // 最大重试3次
        );

        return ReqResult.success(result);
    }

    @Override
    public List<CustomPageConfigModel> list(CustomPageConfigModel model) {
        return customPageConfigRepository.list(model);
    }

    @Override
    public SuperPage<CustomPageConfigModel> pageQuery(CustomPageConfigModel configModel, Long current, Long pageSize) {
        return customPageConfigRepository.pageQuery(configModel, current, pageSize);
    }

    @Override
    public CustomPageConfigModel getById(Long id) {
        return customPageConfigRepository.getById(id);
    }

    @Override
    public CustomPageConfigModel getByAgentId(Long agentId) {
        return customPageConfigRepository.getByAgentId(agentId);
    }

    @Override
    public List<CustomPageConfigModel> listByIds(List<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return List.of();
        }
        return customPageConfigRepository.listByIds(ids);
    }

    @Override
    public ReqResult<CustomPageConfigModel> update(CustomPageConfigModel model, UserContext userContext) {
        log.info("[update] 更新项目, projectId={}", model.getId());

        CustomPageConfigModel configModel = customPageConfigRepository.getById(model.getId());
        if (configModel == null) {
            log.error("[Domain] 项目配置不存在, projectId={}", model.getId());
            return ReqResult.error("0001", "项目配置不存在");
        }

        // 校验空间权限
        spacePermissionService.checkSpaceUserPermission(configModel.getSpaceId());

        // 更新配置
        model.setModifiedId(userContext.getUserId());
        model.setModifiedName(userContext.getUserName());

        customPageConfigRepository.updateById(model, userContext);

        log.info("[update] 更新项目成功, projectId={}", model.getId());
        return ReqResult.success(configModel);
    }

    @Override
    public ReqResult<List<ProxyConfig>> addProxy(Long projectId, ProxyConfig proxyConfig,
                                                 UserContext userContext) {
        log.info("[Domain] 更新反向代理配置, projectId={}, env={}, path={}", projectId, proxyConfig.getEnv(),
                proxyConfig.getPath());

        CustomPageConfigModel configModel = customPageConfigRepository.getById(projectId);
        if (configModel == null) {
            log.error("[Domain] 项目配置不存在, projectId={}", projectId);
            return ReqResult.error("0001", "项目配置不存在");
        }

        // 校验空间权限
        spacePermissionService.checkSpaceUserPermission(configModel.getSpaceId());

        // 获取现有的代理配置列表
        List<ProxyConfig> existingProxyConfigs = Optional.ofNullable(configModel.getProxyConfigs())
                .orElse(new ArrayList<>());

        // 查找是否已存在相同环境和路径的配置
        boolean found = false;
        for (int i = 0; i < existingProxyConfigs.size(); i++) {
            ProxyConfig existing = existingProxyConfigs.get(i);
            if (existing.getEnv() == proxyConfig.getEnv() && existing.getPath().equals(proxyConfig.getPath())) {
                log.error("[Domain] 反向代理配置已存在, projectId={}, env={}, path={}", projectId, proxyConfig.getEnv(),
                        proxyConfig.getPath());
                throw new BizException("0002", "指定环境和路径的代理配置已存在，无法添加");
            }
        }

        // 如果不存在，则添加新配置
        if (!found) {
            existingProxyConfigs.add(proxyConfig);
        }

        CustomPageConfigModel updateModel = new CustomPageConfigModel();
        updateModel.setId(configModel.getId());
        updateModel.setProxyConfigs(existingProxyConfigs);
        updateModel.setModifiedId(userContext.getUserId());
        updateModel.setModifiedName(userContext.getUserName());

        customPageConfigRepository.updateById(updateModel, userContext);

        log.info("[Domain] 更新反向代理配置成功, projectId={}, env={}, path={}",
                projectId, proxyConfig.getEnv(), proxyConfig.getPath());
        return ReqResult.success(existingProxyConfigs);
    }

    @Override
    public ReqResult<Void> editProxy(Long projectId, ProxyConfig proxyConfig, UserContext userContext) {
        log.info("[Domain] 编辑反向代理配置, projectId={}, env={}, path={}", projectId, proxyConfig.getEnv(),
                proxyConfig.getPath());

        CustomPageConfigModel configModel = customPageConfigRepository.getById(projectId);
        if (configModel == null) {
            log.error("[Domain] 项目配置不存在, projectId={}", projectId);
            return ReqResult.error("0001", "项目配置不存在");
        }

        // 校验空间权限
        spacePermissionService.checkSpaceUserPermission(configModel.getSpaceId());

        // 现有的代理配置列表
        List<ProxyConfig> existingProxyConfigs = Optional.ofNullable(configModel.getProxyConfigs())
                .orElse(new ArrayList<>());

        boolean found = false;
        for (int i = 0; i < existingProxyConfigs.size(); i++) {
            ProxyConfig existing = existingProxyConfigs.get(i);
            if (existing.getEnv() == proxyConfig.getEnv() && existing.getPath().equals(proxyConfig.getPath())) {
                existingProxyConfigs.set(i, proxyConfig);
                found = true;
                break;
            }
        }

        if (!found) {
            log.error("[Domain] 反向代理配置不存在, projectId={}, env={}, path={}", projectId, proxyConfig.getEnv(),
                    proxyConfig.getPath());
            throw new BizException("0002", "指定环境和路径的代理配置不存在，无法编辑");
        }

        CustomPageConfigModel updateModel = new CustomPageConfigModel();
        updateModel.setId(configModel.getId());
        updateModel.setProxyConfigs(existingProxyConfigs);
        updateModel.setModifiedId(userContext.getUserId());
        updateModel.setModifiedName(userContext.getUserName());

        customPageConfigRepository.updateById(updateModel, userContext);

        log.info("[Domain] 编辑反向代理配置成功, projectId={}, env={}, path={}",
                projectId, proxyConfig.getEnv(), proxyConfig.getPath());
        return ReqResult.success(null);
    }

    @Override
    public ReqResult<Void> deleteProxy(Long projectId, String env, String path, UserContext userContext) {
        log.info("[Domain] 删除反向代理配置, projectId={}, env={}, path={}", projectId, env, path);

        CustomPageConfigModel configModel = customPageConfigRepository.getById(projectId);
        if (configModel == null) {
            log.error("[Domain] 项目配置不存在, projectId={}", projectId);
            return ReqResult.error("0001", "项目配置不存在");
        }

        // 校验空间权限
        spacePermissionService.checkSpaceUserPermission(configModel.getSpaceId());

        List<ProxyConfig> existingProxyConfigs = Optional.ofNullable(configModel.getProxyConfigs())
                .orElse(new ArrayList<>());

        // 查找要删除的配置
        boolean found = false;
        ProxyConfig.ProxyEnv proxyEnv = ProxyConfig.ProxyEnv.get(env);
        for (int i = 0; i < existingProxyConfigs.size(); i++) {
            ProxyConfig existing = existingProxyConfigs.get(i);
            if (existing.getEnv() == proxyEnv && existing.getPath().equals(path)) {
                // 删除配置
                existingProxyConfigs.remove(i);
                found = true;
                break;
            }
        }

        if (!found) {
            log.error("[Domain] 反向代理配置不存在, projectId={}, env={}, path={}", projectId, env, path);
            throw new BizException("0002", "指定环境和路径的代理配置不存在，无法删除");
        }

        CustomPageConfigModel updateModel = new CustomPageConfigModel();
        updateModel.setId(configModel.getId());
        updateModel.setProxyConfigs(existingProxyConfigs);
        updateModel.setModifiedId(userContext.getUserId());
        updateModel.setModifiedName(userContext.getUserName());

        customPageConfigRepository.updateById(updateModel, userContext);

        log.info("[Domain] 删除反向代理配置成功, projectId={}, env={}, path={}",
                projectId, env, path);
        return ReqResult.success(null);
    }

    @Override
    public ReqResult<Void> savePathArgs(Long projectId, PageArgConfig pageArgConfig, UserContext userContext) {
        log.info("[Domain] 配置路径参数, projectId={}, pageUri={}", projectId, pageArgConfig.getPageUri());

        CustomPageConfigModel configModel = customPageConfigRepository.getById(projectId);
        if (configModel == null) {
            log.error("[Domain] 项目配置不存在, projectId={}", projectId);
            return ReqResult.error("0001", "项目配置不存在");
        }

        // 校验空间权限
        spacePermissionService.checkSpaceUserPermission(configModel.getSpaceId());

        // 验证参数配置
        validatePageArgConfig(pageArgConfig);

        // 现有的页面参数配置列表
        List<PageArgConfig> existingPageArgConfigs = Optional.ofNullable(configModel.getPageArgConfigs())
                .orElse(new ArrayList<>());

        // 查找是否已存在相同pageUri的配置
        boolean found = false;
        for (int i = 0; i < existingPageArgConfigs.size(); i++) {
            PageArgConfig existing = existingPageArgConfigs.get(i);
            if (existing.getPageUri().equals(pageArgConfig.getPageUri())) {
                existingPageArgConfigs.set(i, pageArgConfig);
                found = true;
                break;
            }
        }

        if (!found) {
            existingPageArgConfigs.add(pageArgConfig);
        }

        CustomPageConfigModel updateModel = new CustomPageConfigModel();
        updateModel.setId(configModel.getId());
        updateModel.setPageArgConfigs(existingPageArgConfigs);
        updateModel.setModifiedId(userContext.getUserId());
        updateModel.setModifiedName(userContext.getUserName());

        customPageConfigRepository.updateById(updateModel, userContext);

        log.info("[Domain] 配置路径参数成功, projectId={}, pageUri={}",
                projectId, pageArgConfig.getPageUri());
        return ReqResult.success(null);
    }

    @Override
    public ReqResult<Void> addPath(Long projectId, PageArgConfig pageArgConfig, UserContext userContext) {
        log.info("[Domain] 添加路径配置, projectId={}, pageUri={}", projectId, pageArgConfig.getPageUri());

        CustomPageConfigModel configModel = customPageConfigRepository.getById(projectId);
        if (configModel == null) {
            log.error("[Domain] 项目配置不存在, projectId={}", projectId);
            return ReqResult.error("0001", "项目配置不存在");
        }

        // 校验空间权限
        spacePermissionService.checkSpaceUserPermission(configModel.getSpaceId());

        // 验证参数配置
        validatePageArgConfig(pageArgConfig);

        List<PageArgConfig> existingPageArgConfigs = Optional.ofNullable(configModel.getPageArgConfigs())
                .orElse(new ArrayList<>());

        // 查找是否已存在相同pageUri的配置
        for (PageArgConfig existing : existingPageArgConfigs) {
            if (existing.getPageUri().equals(pageArgConfig.getPageUri())) {
                log.error("[Domain] 路径配置已存在, projectId={}, pageUri={}", projectId, pageArgConfig.getPageUri());
                throw new BizException("0003", "指定路径的配置已存在，无法添加");
            }
        }

        existingPageArgConfigs.add(pageArgConfig);

        CustomPageConfigModel updateModel = new CustomPageConfigModel();
        updateModel.setId(configModel.getId());
        updateModel.setPageArgConfigs(existingPageArgConfigs);
        updateModel.setModifiedId(userContext.getUserId());
        updateModel.setModifiedName(userContext.getUserName());

        customPageConfigRepository.updateById(updateModel, userContext);

        log.info("[Domain] 添加路径配置成功, projectId={}, pageUri={}",
                projectId, pageArgConfig.getPageUri());
        return ReqResult.success(null);
    }

    @Override
    public ReqResult<Void> editPath(Long projectId, PageArgConfig pageArgConfig, UserContext userContext) {
        log.info("[Domain] 编辑路径配置, projectId={}, pageUri={}", projectId, pageArgConfig.getPageUri());

        CustomPageConfigModel configModel = customPageConfigRepository.getById(projectId);
        if (configModel == null) {
            log.error("[Domain] 项目配置不存在, projectId={}", projectId);
            return ReqResult.error("0001", "项目配置不存在");
        }

        // 校验空间权限
        spacePermissionService.checkSpaceUserPermission(configModel.getSpaceId());

        // 验证参数配置
        validatePageArgConfig(pageArgConfig);

        List<PageArgConfig> existingPageArgConfigs = Optional.ofNullable(configModel.getPageArgConfigs())
                .orElse(new ArrayList<>());

        // 查找是否存在相同pageUri的配置
        boolean found = false;
        for (int i = 0; i < existingPageArgConfigs.size(); i++) {
            PageArgConfig existing = existingPageArgConfigs.get(i);
            if (existing.getPageUri().equals(pageArgConfig.getPageUri())) {
                pageArgConfig.setArgs(existing.getArgs());
                existingPageArgConfigs.set(i, pageArgConfig);
                found = true;
                break;
            }
        }

        if (!found) {
            log.error("[Domain] 路径配置不存在, projectId={}, pageUri={}", projectId, pageArgConfig.getPageUri());
            throw new BizException("0004", "指定路径的配置不存在，无法编辑");
        }

        CustomPageConfigModel updateModel = new CustomPageConfigModel();
        updateModel.setId(configModel.getId());
        updateModel.setPageArgConfigs(existingPageArgConfigs);
        updateModel.setModifiedId(userContext.getUserId());
        updateModel.setModifiedName(userContext.getUserName());

        customPageConfigRepository.updateById(updateModel, userContext);

        log.info("[Domain] 编辑路径配置成功, projectId={}, pageUri={}",
                projectId, pageArgConfig.getPageUri());
        return ReqResult.success(null);
    }

    @Override
    public ReqResult<Void> deletePath(Long projectId, String pageUri, UserContext userContext) {
        log.info("[Domain] 删除路径配置, projectId={}, pageUri={}", projectId, pageUri);

        CustomPageConfigModel configModel = customPageConfigRepository.getById(projectId);
        if (configModel == null) {
            log.error("[Domain] 项目配置不存在, projectId={}", projectId);
            return ReqResult.error("0001", "项目配置不存在");
        }

        // 校验空间权限
        spacePermissionService.checkSpaceUserPermission(configModel.getSpaceId());

        List<PageArgConfig> existingPageArgConfigs = Optional.ofNullable(configModel.getPageArgConfigs())
                .orElse(new ArrayList<>());

        // 查找是否存在相同pageUri的配置
        boolean found = false;
        for (int i = 0; i < existingPageArgConfigs.size(); i++) {
            PageArgConfig existing = existingPageArgConfigs.get(i);
            if (existing.getPageUri().equals(pageUri)) {
                existingPageArgConfigs.remove(i);
                found = true;
                break;
            }
        }

        if (!found) {
            log.error("[Domain] 路径配置不存在, projectId={}, pageUri={}", projectId, pageUri);
            throw new BizException("0005", "指定路径的配置不存在，无法删除");
        }

        CustomPageConfigModel updateModel = new CustomPageConfigModel();
        updateModel.setId(configModel.getId());
        updateModel.setPageArgConfigs(existingPageArgConfigs);
        updateModel.setModifiedId(userContext.getUserId());
        updateModel.setModifiedName(userContext.getUserName());

        customPageConfigRepository.updateById(updateModel, userContext);

        log.info("[Domain] 删除路径配置成功, projectId={}, pageUri={}",
                projectId, pageUri);
        return ReqResult.success(null);
    }

    @Override
    public ReqResult<Void> batchConfigProxy(Long projectId, List<ProxyConfig> proxyConfigs, UserContext userContext) {
        log.info("[Domain] 批量配置反向代理, projectId={}, configCount={}", projectId,
                proxyConfigs != null ? proxyConfigs.size() : 0);

        Optional.ofNullable(projectId).filter(x -> x > 0)
                .orElseThrow(() -> new IllegalArgumentException("projectId不能为空或无效"));
        Optional.ofNullable(proxyConfigs)
                .orElseThrow(() -> new IllegalArgumentException("反向代理配置列表不能为空"));

        CustomPageConfigModel configModel = customPageConfigRepository.getById(projectId);
        if (configModel == null) {
            log.error("[Domain] 项目配置不存在, projectId={}", projectId);
            throw new BizException("0001", "项目配置不存在");
        }

        // 验证配置数据
        validateProxyConfigs(proxyConfigs);

        CustomPageConfigModel updateModel = new CustomPageConfigModel();
        updateModel.setId(configModel.getId());
        updateModel.setProxyConfigs(proxyConfigs);
        updateModel.setModifiedId(userContext.getUserId());
        updateModel.setModifiedName(userContext.getUserName());

        customPageConfigRepository.updateById(updateModel, userContext);

        log.info("[Domain] 批量配置反向代理成功, projectId={}, configCount={}",
                projectId, proxyConfigs != null ? proxyConfigs.size() : 0);
        return ReqResult.success(null);
    }

    /**
     * 验证反向代理配置
     */
    private void validateProxyConfigs(List<ProxyConfig> proxyConfigs) {
        if (proxyConfigs.isEmpty()) {
            return; // 允许空列表，表示清空所有配置
        }

        // 检查path重复
        List<String> paths = new ArrayList<>();
        for (ProxyConfig config : proxyConfigs) {
            String pathKey = config.getEnv().name() + ":" + config.getPath();
            if (paths.contains(pathKey)) {
                throw new BizException("0002", "存在重复的路径配置: " + config.getEnv().name() + ":" + config.getPath());
            }
            paths.add(pathKey);

            // 检查backend是否为空
            if (config.getBackends() == null || config.getBackends().isEmpty()) {
                throw new BizException("0003", "后端地址列表不能为空: " + config.getEnv().name() + ":" + config.getPath());
            }

            // 检查每个backend的地址是否为空，并设置默认weight
            for (var backend : config.getBackends()) {
                if (backend.getBackend() == null || backend.getBackend().trim().isEmpty()) {
                    throw new BizException("0004", "后端地址不能为空: " + config.getEnv().name() + ":" + config.getPath());
                }
                // 设置默认weight为1
                if (backend.getWeight() <= 0) {
                    backend.setWeight(1);
                }
            }
        }
    }

    public ReqResult<InputStream> exportProject(Long projectId, ExportTypeEnum exportType, UserContext userContext) {
        log.info("[exportProject] projectId={}, exportType={},开始导出项目", projectId, exportType);

        Optional.ofNullable(projectId).filter(x -> x > 0)
                .orElseThrow(() -> new IllegalArgumentException("projectId不能为空或无效"));

        CustomPageConfigModel configModel = customPageConfigRepository.getById(projectId);
        if (configModel == null) {
            return ReqResult.error("0001", "项目不存在");
        }
        // 校验空间权限
        spacePermissionService.checkSpaceUserPermission(configModel.getSpaceId());

        var buildModel = customPageBuildRepository.getByProjectId(projectId);
        if (buildModel == null) {
            return ReqResult.error("0001", "项目不存在");
        }

        Integer latestVersion = buildModel.getCodeVersion() == null ? 0 : buildModel.getCodeVersion();
        Integer publishedVersion = buildModel.getBuildVersion();
        Integer exportVersion = exportType == ExportTypeEnum.LATEST ? latestVersion : publishedVersion;

        ProjectConfigExportDto configExportDto = exportType == ExportTypeEnum.LATEST ? buildConfigExportDto(configModel) : null;

        InputStream inputStream = pageFileBuildClient.exportProject(projectId, exportVersion, exportType.name(), configExportDto);
        if (inputStream == null) {
            return ReqResult.error("9999", "导出项目失败，server端无响应");
        }

        log.info("[exportProject] 导出项目成功, projectId={}, exportType={}, codeVersion={}", projectId, exportType, exportVersion);
        return ReqResult.success(inputStream);
    }

    /**
     * 构建配置JSON，包含数据源详细信息
     */
    private ProjectConfigExportDto buildConfigExportDto(CustomPageConfigModel configModel) {
        ProjectConfigExportDto configDto = ProjectConfigExportDto.builder()
                .name(configModel.getName())
                .description(configModel.getDescription())
                .icon(configModel.getIcon())
                .coverImg(configModel.getCoverImg())
                .coverImgSourceType(configModel.getCoverImgSourceType())
                .needLogin(configModel.getNeedLogin().equals(YesOrNoEnum.Y.getKey()))
                .proxyConfigs(configModel.getProxyConfigs())
                .pageArgConfigs(configModel.getPageArgConfigs())
                .build();

        if (configModel.getDataSources() != null && !configModel.getDataSources().isEmpty()) {
            List<Map<String, Object>> dataSourcePlugins = new ArrayList<>();
            List<Map<String, Object>> dataSourceWorkflows = new ArrayList<>();

            for (DataSourceDto dataSource : configModel.getDataSources()) {
                try {
                    if ("plugin".equalsIgnoreCase(dataSource.getType())) {
                        Map<String, Object> pluginMap = null;
                        com.xspaceagi.agent.core.sdk.dto.ReqResult<?> errResult = null;
                        // 查询插件详细信息
                        com.xspaceagi.agent.core.sdk.dto.ReqResult<PluginInfoDto> pluginR1 = agentRpcService.getPublishedPluginInfo(dataSource.getId(), null);
                        errResult = pluginR1;

                        if (pluginR1 != null && pluginR1.isSuccess() && pluginR1.getData() != null) {

                            PluginInfoDto pluginInfoDto = pluginR1.getData();
                            Long pluginSpaceId = pluginInfoDto.getSpaceId();

                            try {
                                spacePermissionService.checkSpaceUserPermission(pluginSpaceId);
                            } catch (Exception e) {
                                log.info("[exportProject] projectId={},导出用户无插件空间权限,忽略插件,id={}, error={}", configModel.getId(),
                                        pluginSpaceId, e.getMessage());
                                continue;
                            }

                            com.xspaceagi.agent.core.sdk.dto.ReqResult<String> pluginResult = agentRpcService.queryPluginConfig(dataSource.getId(), null);
                            errResult = pluginResult;

                            if (pluginResult != null && pluginResult.isSuccess()) {
                                pluginMap = new HashMap<>();
                                pluginMap.put("key", dataSource.getKey());
                                pluginMap.put("name", pluginInfoDto.getName());
                                pluginMap.put("icon", pluginInfoDto.getIcon());
                                pluginMap.put("data", pluginResult.getData());
                            }
                        }

                        if (pluginMap != null) {
                            dataSourcePlugins.add(pluginMap);
                        } else {
                            log.warn("[exportProject] projectId={},查询插件详细信息失败,id={}, error={}", configModel.getId(),
                                    dataSource.getId(), errResult != null ? errResult.getMessage() : "无响应");
                            throw new BizException("9999", "查询插件失败,插件[" + dataSource.getId() + ":" + dataSource.getName() + "]");
                        }

                    } else if ("workflow".equalsIgnoreCase(dataSource.getType())) {
                        Map<String, Object> workflowMap = null;
                        com.xspaceagi.agent.core.sdk.dto.ReqResult<?> errResult = null;

                        // 查询工作流详细信息
                        com.xspaceagi.agent.core.sdk.dto.ReqResult<WorkflowInfoDto> workflowR1 = agentRpcService.getPublishedWorkflowInfo(dataSource.getId(), null);
                        errResult = workflowR1;

                        if (workflowR1 != null && workflowR1.isSuccess() && workflowR1.getData() != null) {
                            WorkflowInfoDto workflowInfoDto = workflowR1.getData();
                            Long workflowSpaceId = workflowInfoDto.getSpaceId();

                            try {
                                spacePermissionService.checkSpaceUserPermission(workflowSpaceId);
                            } catch (Exception e) {
                                log.info("[exportProject] projectId={},导出用户无工作流空间权限,忽略工作流,id={}, error={}", configModel.getId(),
                                        workflowSpaceId, e.getMessage());
                                continue;
                            }

                            com.xspaceagi.agent.core.sdk.dto.ReqResult<String> workflowResult = agentRpcService.queryTemplateConfig(TargetTypeEnum.Workflow, dataSource.getId());
                            errResult = workflowResult;

                            if (workflowResult != null && workflowResult.isSuccess()) {
                                workflowMap = new HashMap<>();
                                workflowMap.put("key", dataSource.getKey());
                                workflowMap.put("name", workflowInfoDto.getName());
                                workflowMap.put("icon", workflowInfoDto.getIcon());
                                workflowMap.put("data", workflowResult.getData());
                            }
                        }

                        if (workflowMap != null) {
                            dataSourceWorkflows.add(workflowMap);
                        } else {
                            log.warn("[exportProject] projectId={},查询工作流详细信息失败,id={}, error={}", configModel.getId(),
                                    dataSource.getId(), errResult != null ? errResult.getMessage() : "无响应");
                            throw new BizException("9999", "查询工作流失败,工作流[" + dataSource.getId() + ":" + dataSource.getName() + "]");
                        }

                    }
                } catch (Exception e) {
                    log.warn("[exportProject] projectId={},查询数据源详细信息失败, type={}, id={}, error={}", configModel.getId(),
                            dataSource.getType(), dataSource.getId(), e.getMessage());
                }
            }

            configDto.setDataSourcePlugins(dataSourcePlugins.isEmpty() ? null : dataSourcePlugins);
            configDto.setDataSourceWorkflows(dataSourceWorkflows.isEmpty() ? null : dataSourceWorkflows);
        }
        return configDto;
    }

    @Override
    public ReqResult<Map<String, Object>> queryProjectContent(Long projectId, String command, String proxyPath) {
        log.info("[Domain] 查询项目文件内容, projectId={}, command={}", projectId, command);

        Optional.ofNullable(projectId).filter(x -> x > 0)
                .orElseThrow(() -> new IllegalArgumentException("projectId不能为空或无效"));

        // 校验空间权限
        // CustomPageConfigModel configModel =
        // customPageConfigRepository.getById(projectId);
        // if (configModel == null) {
        // return ReqResult.error("0001", "项目不存在");
        // }
        // spacePermissionService.checkSpaceUserPermission(configModel.getSpaceId());

        Map<String, Object> resp = pageFileBuildClient.getProjectContent(projectId, command, proxyPath);
        if (resp == null) {
            return ReqResult.error("9999", "查询项目文件内容失败，server端无响应");
        }
        boolean success = Boolean.parseBoolean(String.valueOf(resp.get("success")));
        String message = resp.get("message") == null ? "" : String.valueOf(resp.get("message"));
        if (!success) {
            return ReqResult.error("9999", message);
        }
        return ReqResult.success(resp);
    }

    @Override
    public ReqResult<Map<String, Object>> queryProjectContentByVersion(Long projectId, Integer codeVersion, String proxyPath) {
        log.info("[Domain] 查询项目历史版本文件内容, projectId={}, codeVersion={}", projectId, codeVersion);

        Optional.ofNullable(projectId).filter(x -> x > 0)
                .orElseThrow(() -> new IllegalArgumentException("projectId不能为空或无效"));

        Optional.ofNullable(codeVersion).filter(x -> x >= 0)
                .orElseThrow(() -> new IllegalArgumentException("codeVersion不能为空或无效"));

        // 查询版本是否存在
        var buildModel = customPageBuildRepository.getByProjectId(projectId);
        if (buildModel == null) {
            return ReqResult.error("0001", "项目不存在");
        }

        if (codeVersion > buildModel.getCodeVersion()) {
            return ReqResult.error("0002", "指定版本不存在");
        }

        Map<String, Object> resp = null;
        if (codeVersion < buildModel.getCodeVersion()) {
            List<VersionInfoDto> versionInfo = buildModel.getVersionInfo();
            boolean versionExists = false;
            if (versionInfo != null) {
                versionExists = versionInfo.stream()
                        .anyMatch(version -> codeVersion.equals(version.getVersion()));
            }

            if (!versionExists) {
                return ReqResult.error("0002", "指定版本不存在");
            }

            resp = pageFileBuildClient.getProjectContentByVersion(projectId, codeVersion, null, proxyPath);
        } else {
            resp = pageFileBuildClient.getProjectContent(projectId, null, proxyPath);
        }

        if (resp == null) {
            return ReqResult.error("9999", "查询项目历史版本文件内容失败，server端无响应");
        }
        boolean success = Boolean.parseBoolean(String.valueOf(resp.get("success")));
        String message = resp.get("message") == null ? "" : String.valueOf(resp.get("message"));
        if (!success) {
            return ReqResult.error("9999", message);
        }
        return ReqResult.success(resp);
    }

    /**
     * 验证页面参数配置
     */
    private void validatePageArgConfig(PageArgConfig pageArgConfig) {
        if (pageArgConfig.getArgs() == null || pageArgConfig.getArgs().isEmpty()) {
            return; // 允许空参数列表
        }

        // 检查args中是否有重复的name
        List<String> names = new ArrayList<>();
        for (PageArg arg : pageArgConfig.getArgs()) {
            if (arg.getName() == null || arg.getName().trim().isEmpty()) {
                throw new BizException("0005", "参数名称不能为空");
            }

            if (names.contains(arg.getName())) {
                throw new BizException("0006", "存在重复的参数名称: " + arg.getName());
            }
            names.add(arg.getName());
        }
    }

    @Override
    public ReqResult<Void> bindDataSource(Long projectId, DataSourceDto dataSource, UserContext userContext) {
        log.info("[Domain] 保存数据源, projectId={}, type={}, dataSourceId={}",
                projectId, dataSource.getType(), dataSource.getId());

        if (dataSource.getType() == null || dataSource.getType().trim().isEmpty()) {
            log.error("[Domain] 数据源类型不能为空, projectId={}", projectId);
            return ReqResult.error("0003", "数据源类型不能为空");
        }

        if (dataSource.getId() == null || dataSource.getId() <= 0) {
            log.error("[Domain] 数据源ID不能为空或无效, projectId={}", projectId);
            return ReqResult.error("0004", "数据源ID不能为空或无效");
        }

        if (dataSource.getName() == null || dataSource.getName().trim().isEmpty()) {
            log.error("[Domain] 数据源名称不能为空, projectId={}", projectId);
            return ReqResult.error("0005", "数据源名称不能为空");
        }

        CustomPageConfigModel configModel = customPageConfigRepository.getById(projectId);
        if (configModel == null) {
            log.error("[Domain] 项目不存在, projectId={}", projectId);
            return ReqResult.error("0001", "项目不存在");
        }

        // 校验空间权限
        spacePermissionService.checkSpaceUserPermission(configModel.getSpaceId());

        // 现有的数据源列表
        List<DataSourceDto> existingDataSources = Optional.ofNullable(configModel.getDataSources())
                .orElse(new ArrayList<>());

        // 检查是否已存在相同类型和ID的数据源，如果存在则更新，否则添加
        boolean found = false;
        for (int i = 0; i < existingDataSources.size(); i++) {
            DataSourceDto existing = existingDataSources.get(i);
            if (existing.getType().equals(dataSource.getType()) && existing.getId().equals(dataSource.getId())) {
                // 更新现有数据源
                existingDataSources.set(i, dataSource);
                found = true;
                log.info("[Domain] 更新现有数据源, projectId={}, type={}, id={}",
                        projectId, dataSource.getType(), dataSource.getId());
                break;
            }
        }

        if (!found) {
            existingDataSources.add(dataSource);
            log.info("[Domain] 添加新数据源, projectId={}, type={}, id={}",
                    projectId, dataSource.getType(), dataSource.getId());
        }

        CustomPageConfigModel updateModel = new CustomPageConfigModel();
        updateModel.setId(configModel.getId());
        updateModel.setDataSources(existingDataSources);
        updateModel.setModifiedId(userContext.getUserId());
        updateModel.setModifiedName(userContext.getUserName());

        customPageConfigRepository.updateById(updateModel, userContext);

        log.info("[Domain] 保存数据源成功, projectId={}, type={}, id={}",
                projectId, dataSource.getType(), dataSource.getId());
        return ReqResult.success(null);
    }

    @Override
    public ReqResult<Void> unbindDataSource(Long projectId, DataSourceDto dataSource, UserContext userContext) {
        log.info("[unbindDataSource] 解绑数据源, projectId={}, type={}, dataSourceId={}",
                projectId, dataSource.getType(), dataSource.getId());

        CustomPageConfigModel configModel = customPageConfigRepository.getById(projectId);
        if (configModel == null) {
            log.error("[unbindDataSource] 项目不存在, projectId={}", projectId);
            return ReqResult.error("0001", "项目配置不存在");
        }

        // 校验空间权限
        spacePermissionService.checkSpaceUserPermission(configModel.getSpaceId());

        // 现有的数据源列表
        List<DataSourceDto> existingDataSources = Optional.ofNullable(configModel.getDataSources())
                .orElse(new ArrayList<>());

        boolean found = false;
        for (int i = 0; i < existingDataSources.size(); i++) {
            DataSourceDto existing = existingDataSources.get(i);
            if (existing.getType().equals(dataSource.getType()) && existing.getId().equals(dataSource.getId())) {
                // 删除现有数据源
                existingDataSources.remove(existing);
                found = true;
                log.info("[unbindDataSource] 解绑数据源, projectId={}, type={}, id={}",
                        projectId, dataSource.getType(), dataSource.getId());
                break;
            }
        }

        if (!found) {
            log.info("[unbindDataSource] 未找到要解绑的数据源, projectId={}, type={}, id={}",
                    projectId, dataSource.getType(), dataSource.getId());
            return ReqResult.create("0000", "未找到要解绑的数据源", null);
        }

        CustomPageConfigModel updateModel = new CustomPageConfigModel();
        updateModel.setId(configModel.getId());
        updateModel.setDataSources(existingDataSources);
        updateModel.setModifiedId(userContext.getUserId());
        updateModel.setModifiedName(userContext.getUserName());

        customPageConfigRepository.updateById(updateModel, userContext);

        log.info("[Domain] 解绑数据源成功, projectId={}, type={}, id={}",
                projectId, dataSource.getType(), dataSource.getId());
        return ReqResult.success(null);
    }

    @Override
    public ReqResult<Map<String, Object>> delete(Long projectId, UserContext userContext) {
        log.info("[Domain] 删除项目, projectId={}", projectId);

        Optional.ofNullable(projectId).filter(x -> x > 0)
                .orElseThrow(() -> new IllegalArgumentException("项目ID不能为空或无效"));

        CustomPageConfigModel configModel = customPageConfigRepository.getById(projectId);
        if (configModel == null) {
            log.error("[Domain] 项目配置不存在, projectId={}", projectId);
            throw new BizException("0001", "项目配置不存在");
        }
        CustomPageBuildModel buildModel = customPageBuildRepository.getByProjectId(projectId);

        // 校验空间权限
        spacePermissionService.checkSpaceUserPermission(configModel.getSpaceId());

        // 清除缓存
        customPageProxyPathService.removeConfigCache(projectId);

        // 删除保活缓存
        keepAliveService.removeKeepAliveCache(projectId);

        // 删除config
        customPageConfigRepository.deleteById(projectId, userContext);
        // 删除build
        customPageBuildRepository.deleteByProjectId(projectId, userContext);

        log.info("[Domain] 删除项目成功, projectId={}", projectId);
        Map<String, Object> map = new HashMap<>();
        map.put("config", configModel);
        map.put("build", buildModel);
        return ReqResult.success(map);
    }

    /**
     * 导入项目配置文件
     * 如果上传的压缩包中包含 cpage_config.json 文件，则解析并应用配置
     */
    public void importProjectConfig(CustomPageConfigModel model, UserContext userContext) {
        Long projectId = model.getId();
        log.info("[upload-project] projectId={},开始导入配置文件", projectId);

        String proxyPath = "/page/static/" + projectId;
        // 获取项目文件内容
        ReqResult<Map<String, Object>> contentResult = this.queryProjectContent(projectId, "cpage_config", proxyPath);
        if (!contentResult.isSuccess()) {
            log.warn("[upload-project] projectId={},获取项目文件内容失败", projectId);
            return;
        }

        Map<String, Object> data = contentResult.getData();
        Object filesObj = data.get("files");
        if (filesObj == null) {
            log.info("[upload-project] projectId={},项目文件列表为空", projectId);
            return;
        }

        // 查找 cpage_config.json 文件
        if (!(filesObj instanceof List)) {
            log.warn("[upload-project] projectId={},filesObj 不是 List 类型", projectId);
            return;
        }

        // 将 List 中的 LinkedHashMap 元素转换为 PageFileInfo 对象
        List<PageFileInfo> files = new ArrayList<>();
        @SuppressWarnings("unchecked")
        List<Object> rawFiles = (List<Object>) filesObj;
        for (Object rawFile : rawFiles) {
            PageFileInfo fileInfo = JSON.parseObject(JSON.toJSONString(rawFile), PageFileInfo.class);
            files.add(fileInfo);
        }

        PageFileInfo configFile = null;
        for (PageFileInfo file : files) {
            if ("cpage_config.json".equals(file.getName())) {
                configFile = file;
                break;
            }
        }

        if (configFile == null) {
            log.info("[upload-project] projectId={},未找到 cpage_config.json 文件", projectId);
            return;
        }
        log.info("[upload-project] projectId={},找到 cpage_config.json 文件,开始解析配置", projectId);

        // 解析配置文件
        try {
            ProjectConfigExportDto configDto = JSON.parseObject(configFile.getContents(),
                    ProjectConfigExportDto.class);
            if (configDto == null) {
                log.warn("[upload-project] projectId={},配置文件解析失败", projectId);
                return;
            }
            log.info("[upload-project] projectId={},配置文件已解析,configDto={}", projectId, configDto);

            // 应用配置
            applyProjectConfig(model, configDto, userContext);

            log.info("[upload-project] projectId={},配置文件导入成功", projectId);
        } catch (Exception e) {
            log.error("[upload-project] projectId={},配置文件解析失败", projectId, e);
            throw new BizException("配置文件解析失败: " + e.getMessage());
        }
    }

    /**
     * 应用项目配置
     */
    private void applyProjectConfig(CustomPageConfigModel model, ProjectConfigExportDto configDto,
                                    UserContext userContext) {
        Long projectId = model.getId();
        log.info("[upload-project] projectId={},开始应用配置", projectId);

        CustomPageConfigModel updateModel = new CustomPageConfigModel();
        updateModel.setId(projectId);

        // 更新icon
        if (configDto.getIcon() != null && model.getIcon() == null) {
            updateModel.setIcon(configDto.getIcon());
        }

        // 更新 coverImg
        if (configDto.getCoverImg() != null && model.getCoverImg() == null) {
            updateModel.setCoverImg(configDto.getCoverImg());
        }

        // 更新 coverImgSourceType
        if (configDto.getCoverImgSourceType() != null && model.getCoverImgSourceType() == null) {
            updateModel.setCoverImgSourceType(configDto.getCoverImgSourceType());
        }

        // 更新 needLogin
        if (configDto.getNeedLogin() != null) {
            updateModel.setNeedLogin(
                    configDto.getNeedLogin() ? YesOrNoEnum.Y.getKey() : YesOrNoEnum.N.getKey());
        }

        // 更新 proxyConfigs
        if (configDto.getProxyConfigs() != null && !configDto.getProxyConfigs().isEmpty()) {
            updateModel.setProxyConfigs(configDto.getProxyConfigs());
        }

        // 更新 pageArgConfigs
        if (configDto.getPageArgConfigs() != null && !configDto.getPageArgConfigs().isEmpty()) {
            updateModel.setPageArgConfigs(configDto.getPageArgConfigs());
        }

        // 创建插件和工作流，准备数据源数据
        List<DataSourceDto> dataSources = new ArrayList<>();

        // 创建插件
        if (configDto.getDataSourcePlugins() != null && !configDto.getDataSourcePlugins().isEmpty()) {
            log.info("[upload-project] projectId={},开始创建插件,count={}", projectId,
                    configDto.getDataSourcePlugins().size());
            for (Map<String, Object> pluginMap : configDto.getDataSourcePlugins()) {
                try {
                    String pluginName = (String) pluginMap.get("name");
                    String pluginIcon = (String) pluginMap.get("icon");
                    String pluginKey = (String) pluginMap.get("key");
                    String pluginData = (String) pluginMap.get("data");

                    PluginEnableOrUpdateDto pluginDto = new PluginEnableOrUpdateDto();
                    pluginDto.setUserId(userContext.getUserId());
                    pluginDto.setSpaceId(model.getSpaceId());
                    pluginDto.setName(pluginName);
                    pluginDto.setIcon(pluginIcon);
                    pluginDto.setConfig(pluginData);
                    pluginDto.setParamJson(pluginMap.get("paramJson") != null
                            ? pluginMap.get("paramJson").toString()
                            : "{}");

                    com.xspaceagi.agent.core.sdk.dto.ReqResult<Long> pluginResult = agentRpcService
                            .pluginEnableOrUpdate(pluginDto);
                    if (!pluginResult.isSuccess()) {
                        log.error("[upload-project] projectId={},创建插件失败,message={}",
                                projectId,
                                pluginResult.getMessage());
                        // throw new BizException("创建插件失败: " + pluginResult.getMessage());
                        continue;
                    } else {
                        log.info("[upload-project] projectId={},创建插件成功,pluginId={}",
                                projectId, pluginResult.getData());
                    }
                    Long pluginId = pluginResult.getData();
                    if (pluginId != null) {
                        DataSourceDto dataSource = DataSourceDto.builder()
                                .type("plugin")
                                .id(pluginId)
                                .name(pluginName)
                                .icon(pluginIcon)
                                .key(pluginKey)
                                .build();
                        dataSources.add(dataSource);
                    }
                } catch (Exception e) {
                    log.error("[upload-project] projectId={},创建插件失败", projectId, e);
                }
            }
        }

        // 创建工作流
        if (configDto.getDataSourceWorkflows() != null && !configDto.getDataSourceWorkflows().isEmpty()) {
            log.info("[upload-project] projectId={},开始创建工作流,count={}", projectId,
                    configDto.getDataSourceWorkflows().size());
            for (Map<String, Object> workflowMap : configDto.getDataSourceWorkflows()) {
                try {
                    String workflowName = (String) workflowMap.get("name");
                    String workflowIcon = (String) workflowMap.get("icon");
                    String workflowKey = (String) workflowMap.get("key");
                    String workflowData = (String) workflowMap.get("data");

                    TemplateEnableOrUpdateDto templateDto = new TemplateEnableOrUpdateDto();
                    templateDto.setUserId(userContext.getUserId());
                    templateDto.setTargetType(TargetTypeEnum.Workflow);
                    templateDto.setName(workflowName);
                    templateDto.setIcon(workflowIcon);
                    templateDto.setConfig(workflowData);
                    templateDto.setSpaceId(model.getSpaceId());

                    com.xspaceagi.agent.core.sdk.dto.ReqResult<Long> templateResult = agentRpcService
                            .templateEnableOrUpdate(templateDto);
                    if (!templateResult.isSuccess()) {
                        log.error("[upload-project] projectId={},创建工作流失败", projectId);
                        // throw new BizException("创建工作流失败: " + templateResult.getMessage());
                        continue;
                    } else {
                        log.info("[upload-project] projectId={},创建工作流成功,workflowId={}",
                                projectId, templateResult.getData());
                    }
                    Long workflowId = templateResult.getData();
                    if (workflowId != null) {
                        DataSourceDto dataSource = DataSourceDto.builder()
                                .type("workflow")
                                .id(workflowId)
                                .name(workflowName)
                                .icon(workflowIcon)
                                .key(workflowKey)
                                .build();
                        dataSources.add(dataSource);
                    }
                } catch (Exception e) {
                    log.error("[upload-project] projectId={},创建工作流失败", projectId, e);
                }
            }
        }

        if (!dataSources.isEmpty()) {
            updateModel.setDataSources(dataSources);
            log.info("[upload-project] projectId={},绑定数据源,dataSources={}", projectId, dataSources);
        }

        // 统一更新项目配置
        if (updateModel.getNeedLogin() != null
                || updateModel.getProxyConfigs() != null
                || updateModel.getPageArgConfigs() != null
                || updateModel.getDataSources() != null) {
            updateModel.setModifiedId(userContext.getUserId());
            updateModel.setModifiedName(userContext.getUserName());

            customPageConfigRepository.updateById(updateModel, userContext);

            log.info("[upload-project] projectId={},更新项目配置成功", projectId);
        }
    }

    @Override
    public List<CustomPageConfigModel> listByDevAgentIds(List<Long> devAgentIds) {
        return customPageConfigRepository.listByDevAgentIds(devAgentIds);
    }

    @Override
    public Long countTotalPages() {
        return customPageConfigRepository.countTotalPages();
    }
}