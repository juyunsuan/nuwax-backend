package com.xspaceagi.custompage.application.service.impl;

import com.baomidou.dynamic.datasource.annotation.DSTransactional;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.xspaceagi.agent.core.adapter.application.PluginApplicationService;
import com.xspaceagi.agent.core.adapter.application.PublishApplicationService;
import com.xspaceagi.agent.core.adapter.application.WorkflowApplicationService;
import com.xspaceagi.agent.core.adapter.dto.PublishedDto;
import com.xspaceagi.agent.core.adapter.dto.PublishedPermissionDto;
import com.xspaceagi.agent.core.adapter.dto.config.plugin.PluginDto;
import com.xspaceagi.agent.core.adapter.dto.config.workflow.WorkflowConfigDto;
import com.xspaceagi.agent.core.adapter.repository.entity.Published;
import com.xspaceagi.agent.core.sdk.IAgentRpcService;
import com.xspaceagi.agent.core.sdk.dto.*;
import com.xspaceagi.agent.core.sdk.enums.TargetTypeEnum;
import com.xspaceagi.custompage.application.service.ICustomPageConfigApplicationService;
import com.xspaceagi.custompage.domain.model.CustomPageBuildModel;
import com.xspaceagi.custompage.domain.model.CustomPageConfigModel;
import com.xspaceagi.custompage.domain.service.ICustomPageBuildDomainService;
import com.xspaceagi.custompage.domain.service.ICustomPageConfigDomainService;
import com.xspaceagi.custompage.sdk.dto.DataSourceDto;
import com.xspaceagi.custompage.sdk.dto.ExportTypeEnum;
import com.xspaceagi.custompage.sdk.dto.PageArgConfig;
import com.xspaceagi.custompage.sdk.dto.ProxyConfig;
import com.xspaceagi.system.application.service.SysUserPermissionCacheService;
import com.xspaceagi.system.sdk.server.IUserDataPermissionRpcService;
import com.xspaceagi.system.sdk.service.dto.UserDataPermissionDto;
import com.xspaceagi.system.spec.common.UserContext;
import com.xspaceagi.system.spec.dto.ReqResult;
import com.xspaceagi.system.spec.exception.BizException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 自定义页面配置应用服务实现
 */
@Slf4j
@Service
public class CustomPageConfigApplicationServiceImpl implements ICustomPageConfigApplicationService {

    @Resource
    private ICustomPageConfigDomainService customPageConfigDomainService;
    @Resource
    private ICustomPageBuildDomainService customPageBuildDomainService;
    @Resource
    private PluginApplicationService pluginApplicationService;
    @Resource
    private WorkflowApplicationService workflowApplicationService;
    @Resource
    private IAgentRpcService agentRpcService;
    @Resource
    private PublishApplicationService publishApplicationService;
    @Resource
    private IUserDataPermissionRpcService userDataPermissionRpcService;
    @Resource
    private SysUserPermissionCacheService sysUserPermissionCacheService;

    // 需要事务
    @Transactional(rollbackFor = Exception.class)
    @Override
    public ReqResult<CustomPageConfigModel> create(CustomPageConfigModel model, UserContext userContext)
            throws JsonProcessingException {
        log.info("[create] 创建项目,name={}", model.getName());

        Optional.ofNullable(model.getName()).filter(StringUtils::isNotBlank)
                .orElseThrow(() -> new IllegalArgumentException("projectName不能为空"));
        if (model.getSpaceId() == null) {
            throw new IllegalArgumentException("spaceId不能为空");
        }

        // 校验用户网页应用数量是否超限
        UserDataPermissionDto dataPermission = userDataPermissionRpcService.getUserDataPermission(userContext.getUserId());
        if (dataPermission != null) {
            Integer maxPageAppCount = dataPermission.getMaxPageAppCount();
            if (maxPageAppCount != null && maxPageAppCount != -1) {
                CustomPageConfigModel queryModel = new CustomPageConfigModel();
                queryModel.setCreatorId(userContext.getUserId());
                List<CustomPageConfigModel> existingPages = customPageConfigDomainService.list(queryModel);
                int currentCount = existingPages == null ? 0 : existingPages.size();
                if (currentCount >= maxPageAppCount) {
                    log.warn("[create] 创建项目失败，用户页面数量已达上限，userId={}, currentCount={}, maxPageAppCount={}", userContext.getUserId(), currentCount, maxPageAppCount);
                    throw new BizException("你的网页应用数量已经达到上限，无法网页应用上限数为" + maxPageAppCount);
                }
            }
        }

        // 创建config表
        ReqResult<CustomPageConfigModel> configResult = customPageConfigDomainService.create(model,
                userContext);

        if (!configResult.isSuccess()) {
            log.error("[create] 创建项目失败(config表), name={}, basePath={}, error={}",
                    model.getName(), model.getBasePath(), configResult.getMessage());
            throw new BizException("创建自定义页面(config)失败: " + configResult.getMessage());
        }
        CustomPageConfigModel configModel = configResult.getData();

        // 创建build表
        Long projectId = configModel.getId();
        ReqResult<CustomPageBuildModel> buildResult = customPageBuildDomainService.createProject(projectId,
                model.getSpaceId(),
                userContext);

        if (!buildResult.isSuccess()) {
            log.error("[create] 创建项目失败(build表), name={}, basePath={}, error={}",
                    model.getName(), model.getBasePath(), buildResult.getMessage());
            throw new BizException("创建自定义页面(build)失败: " + buildResult.getMessage());
        }

        // 创建智能体
        PageAppAgentCreateDto agentDto = new PageAppAgentCreateDto();
        agentDto.setCreatorId(userContext.getUserId());
        agentDto.setSpaceId(model.getSpaceId());
        agentDto.setName(model.getName());
        agentDto.setDescription(model.getDescription());
        agentDto.setIcon(model.getIcon());
        agentDto.setProjectId(projectId);
        com.xspaceagi.agent.core.sdk.dto.ReqResult<Long> agentResult = agentRpcService
                .createPageAppAgent(agentDto);

        if (!agentResult.isSuccess()) {
            log.error("[create] 创建智能体失败, name={}, error={}", model.getName(), agentResult.getMessage());
            throw new BizException("创建智能体失败: " + agentResult.getMessage());
        }

        // 绑定智能体到项目
        CustomPageConfigModel bindModel = new CustomPageConfigModel();
        bindModel.setId(projectId);
        bindModel.setDevAgentId(agentResult.getData());
        ReqResult<CustomPageConfigModel> result = customPageConfigDomainService.update(
                bindModel,
                userContext);
        if (!result.isSuccess()) {
            log.error("[create] projectId={},绑定智能体到项目失败,message={}", projectId,
                    result.getMessage());
            throw new BizException("绑定智能体到项目失败: " + result.getMessage());
        }

        log.info("[create] 创建项目成功, projectId={}, name={}", projectId, model.getName());
        configResult.getData().setDevAgentId(agentResult.getData());
        return ReqResult.success(configResult.getData());
    }

    // 不需要事务
    @Override
    public ReqResult<Map<String, Object>> uploadProject(CustomPageConfigModel model, MultipartFile file,
                                                        boolean isInitProject,
                                                        UserContext userContext) throws Exception {
        log.info("[upload-project] projectId={}开始上传", model.getId());
        if (file == null || file.isEmpty()) {
            return ReqResult.error("0001", "文件不能为空");
        }
        Long projectId = model.getId();

        // 传输压缩包
        ReqResult<Map<String, Object>> result = customPageBuildDomainService.uploadProject(
                projectId,
                file,
                isInitProject,
                userContext);

        if (!result.isSuccess()) {
            log.info("[upload-project] 上传失败, projectId={}, code={}, message={}",
                    projectId,
                    result.getCode(), result.getMessage());
            return ReqResult.error(result.getCode(), result.getMessage());
        }
        log.info("[upload-project] 上传成功, projectId={}, result={}", projectId, result);

        // 如果是创建新项目，尝试导入配置文件
        if (isInitProject) {
            try {
                customPageConfigDomainService.importProjectConfig(model, userContext);
            } catch (Exception e) {
                log.error("[upload-project] projectId={},配置文件导入失败", projectId, e);
                // 配置文件导入失败不影响整体上传流程
            }
        }

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReqResult<CustomPageConfigModel> createReverseProxyProject(CustomPageConfigModel model,
                                                                      UserContext userContext) {
        log.info("[createProxyProject] 创建反向代理项目, name={}", model.getName());

        Optional.ofNullable(model.getName()).filter(StringUtils::isNotBlank)
                .orElseThrow(() -> new IllegalArgumentException("projectName不能为空"));
        if (model.getSpaceId() == null) {
            throw new IllegalArgumentException("spaceId不能为空");
        }

        ReqResult<CustomPageConfigModel> configResult = customPageConfigDomainService.create(model, userContext);

        if (!configResult.isSuccess()) {
            log.error("[createProxyProject] 创建反向代理项目失败, name={}, message={}",
                    model.getName(), configResult.getMessage());
            throw new BizException("创建反向代理项目失败: " + configResult.getMessage());
        }

        log.info("[createProxyProject] 创建反向代理项目成功, projectId={}, name={}",
                configResult.getData().getId(), model.getName());
        return ReqResult.success(configResult.getData());
    }

    @Override
    public ReqResult<Map<String, Object>> queryProjectContent(Long projectId, String proxyPath) {
        log.info("[queryProjectContent] projectId={},查询项目文件内容", projectId);
        Optional.ofNullable(projectId).filter(x -> x > 0)
                .orElseThrow(() -> new IllegalArgumentException("projectId不能为空或无效"));

        ReqResult<Map<String, Object>> result = customPageConfigDomainService.queryProjectContent(projectId,
                null, proxyPath);
        if (!result.isSuccess()) {
            log.error("[queryProjectContent] projectId={},查询项目文件内容失败,message={}", projectId,
                    result.getMessage());
            return ReqResult.error(result.getCode(), result.getMessage());
        }

        log.info("[queryProjectContent] projectId={},查询项目文件内容成功", projectId);
        return result;
    }

    @Override
    public ReqResult<Map<String, Object>> queryProjectContentByVersion(Long projectId, Integer codeVersion, String proxyPath) {
        log.info("[queryProjectContentByVersion] projectId={},codeVersion={},查询项目历史版本文件内容", projectId,
                codeVersion);

        ReqResult<Map<String, Object>> result = customPageConfigDomainService
                .queryProjectContentByVersion(projectId, codeVersion, proxyPath);
        if (!result.isSuccess()) {
            log.error("[queryProjectContentByVersion] projectId={},codeVersion={},查询项目历史版本文件内容失败,message={}",
                    projectId,
                    codeVersion, result.getMessage());
            return ReqResult.error(result.getCode(), result.getMessage());
        }

        log.info("[queryProjectContentByVersion] projectId={},codeVersion={},查询项目历史版本文件内容成功", projectId,
                codeVersion);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReqResult<List<ProxyConfig>> addProxy(Long projectId, ProxyConfig proxyConfig,
                                                 UserContext userContext) {
        log.info("[addProxy] projectId={},env={},path={},添加反向代理配置", projectId, proxyConfig.getEnv(),
                proxyConfig.getPath());

        Optional.ofNullable(projectId).filter(x -> x > 0)
                .orElseThrow(() -> new IllegalArgumentException("projectId不能为空或无效"));
        Optional.ofNullable(proxyConfig.getEnv())
                .orElseThrow(() -> new IllegalArgumentException("环境不能为空"));
        Optional.ofNullable(proxyConfig.getPath()).filter(StringUtils::isNotBlank)
                .orElseThrow(() -> new IllegalArgumentException("路径不能为空"));
        Optional.ofNullable(proxyConfig.getBackends()).filter(list -> !list.isEmpty())
                .orElseThrow(() -> new IllegalArgumentException("后端地址列表不能为空"));

        ReqResult<List<ProxyConfig>> result = customPageConfigDomainService.addProxy(projectId,
                proxyConfig, userContext);

        if (!result.isSuccess()) {
            log.error("[addProxy] projectId={},env={},path={},添加反向代理配置失败,message={}",
                    projectId, proxyConfig.getEnv(), proxyConfig.getPath(), result.getMessage());
            return result;
        }

        log.info("[addProxy] projectId={},env={},path={},添加反向代理配置成功",
                projectId, proxyConfig.getEnv(), proxyConfig.getPath());
        return ReqResult.success(result.getData());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReqResult<Void> editProxyConfig(Long projectId, ProxyConfig proxyConfig, UserContext userContext) {
        log.info("[editProxyConfig] projectId={},env={},path={},编辑反向代理配置", projectId, proxyConfig.getEnv(),
                proxyConfig.getPath());

        Optional.ofNullable(projectId).filter(x -> x > 0)
                .orElseThrow(() -> new IllegalArgumentException("projectId不能为空或无效"));
        Optional.ofNullable(proxyConfig.getEnv())
                .orElseThrow(() -> new IllegalArgumentException("环境不能为空"));
        Optional.ofNullable(proxyConfig.getPath()).filter(StringUtils::isNotBlank)
                .orElseThrow(() -> new IllegalArgumentException("路径不能为空"));
        Optional.ofNullable(proxyConfig.getBackends()).filter(list -> !list.isEmpty())
                .orElseThrow(() -> new IllegalArgumentException("后端地址列表不能为空"));

        ReqResult<Void> result = customPageConfigDomainService.editProxy(projectId, proxyConfig,
                userContext);

        if (!result.isSuccess()) {
            log.error("[editProxyConfig] projectId={},env={},path={},编辑反向代理配置失败,message={}",
                    projectId, proxyConfig.getEnv(), proxyConfig.getPath(), result.getMessage());
            return result;
        }

        log.info("[editProxyConfig] projectId={},env={},path={},编辑反向代理配置成功",
                projectId, proxyConfig.getEnv(), proxyConfig.getPath());
        return ReqResult.success(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReqResult<Void> deleteProxy(Long projectId, String env, String path, UserContext userContext) {
        log.info("[deleteProxy] projectId={},env={},path={},删除反向代理配置", projectId, env, path);

        Optional.ofNullable(projectId).filter(x -> x > 0)
                .orElseThrow(() -> new IllegalArgumentException("projectId不能为空或无效"));
        Optional.ofNullable(env).filter(StringUtils::isNotBlank)
                .orElseThrow(() -> new IllegalArgumentException("环境不能为空"));
        Optional.ofNullable(path).filter(StringUtils::isNotBlank)
                .orElseThrow(() -> new IllegalArgumentException("路径不能为空"));

        ReqResult<Void> result = customPageConfigDomainService.deleteProxy(projectId, env, path, userContext);

        if (!result.isSuccess()) {
            log.error("[deleteProxy] projectId={},env={},path={},删除反向代理配置失败,message={}",
                    projectId, env, path, result.getMessage());
            throw new BizException("删除反向代理配置失败: " + result.getMessage());
        }

        log.info("[deleteProxy] projectId={},env={},path={},删除反向代理配置成功",
                projectId, env, path);
        return ReqResult.success(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReqResult<Void> savePathArgs(Long projectId, PageArgConfig pageArgConfig, UserContext userContext) {
        log.info("[savePathArgs] projectId={},pageUri={},配置页面参数", projectId, pageArgConfig.getPageUri());

        Optional.ofNullable(projectId).filter(x -> x > 0)
                .orElseThrow(() -> new IllegalArgumentException("projectId不能为空或无效"));
        Optional.ofNullable(pageArgConfig.getPageUri()).filter(StringUtils::isNotBlank)
                .orElseThrow(() -> new IllegalArgumentException("页面路径不能为空"));

        ReqResult<Void> result = customPageConfigDomainService.savePathArgs(projectId, pageArgConfig,
                userContext);

        if (!result.isSuccess()) {
            log.error("[savePathArgs] projectId={},pageUri={},配置页面参数失败,message={}",
                    projectId, pageArgConfig.getPageUri(), result.getMessage());
            throw new BizException("配置页面参数失败: " + result.getMessage());
        }

        log.info("[savePathArgs] projectId={},pageUri={},配置页面参数成功",
                projectId, pageArgConfig.getPageUri());
        return ReqResult.success(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReqResult<Void> addPath(Long projectId, PageArgConfig pageArgConfig, UserContext userContext) {
        log.info("[addPath] projectId={},pageUri={},添加路径配置", projectId, pageArgConfig.getPageUri());

        Optional.ofNullable(projectId).filter(x -> x > 0)
                .orElseThrow(() -> new IllegalArgumentException("projectId不能为空或无效"));
        Optional.ofNullable(pageArgConfig.getPageUri()).filter(StringUtils::isNotBlank)
                .orElseThrow(() -> new IllegalArgumentException("页面路径不能为空"));

        ReqResult<Void> result = customPageConfigDomainService.addPath(projectId, pageArgConfig,
                userContext);

        if (!result.isSuccess()) {
            log.error("[addPath] projectId={},pageUri={},添加路径配置失败,message={}",
                    projectId, pageArgConfig.getPageUri(), result.getMessage());
            throw new BizException("添加路径配置失败: " + result.getMessage());
        }

        log.info("[addPath] projectId={},pageUri={},添加路径配置成功",
                projectId, pageArgConfig.getPageUri());
        return ReqResult.success(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReqResult<Void> editPath(Long projectId, PageArgConfig pageArgConfig, UserContext userContext) {
        log.info("[editPath] projectId={},pageUri={},编辑路径配置", projectId, pageArgConfig.getPageUri());

        Optional.ofNullable(projectId).filter(x -> x > 0)
                .orElseThrow(() -> new IllegalArgumentException("projectId不能为空或无效"));
        Optional.ofNullable(pageArgConfig.getPageUri()).filter(StringUtils::isNotBlank)
                .orElseThrow(() -> new IllegalArgumentException("页面路径不能为空"));

        ReqResult<Void> result = customPageConfigDomainService.editPath(projectId, pageArgConfig,
                userContext);

        if (!result.isSuccess()) {
            log.error("[editPath] projectId={},pageUri={},编辑路径配置失败,message={}",
                    projectId, pageArgConfig.getPageUri(), result.getMessage());
            throw new BizException("编辑路径配置失败: " + result.getMessage());
        }

        log.info("[editPath] projectId={},pageUri={},编辑路径配置成功",
                projectId, pageArgConfig.getPageUri());
        return ReqResult.success(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReqResult<Void> deletePath(Long projectId, String pageUri, UserContext userContext) {
        log.info("[deletePath] projectId={},pageUri={},删除路径配置", projectId, pageUri);

        Optional.ofNullable(projectId).filter(x -> x > 0)
                .orElseThrow(() -> new IllegalArgumentException("projectId不能为空或无效"));
        Optional.ofNullable(pageUri).filter(StringUtils::isNotBlank)
                .orElseThrow(() -> new IllegalArgumentException("页面路径不能为空"));

        ReqResult<Void> result = customPageConfigDomainService.deletePath(projectId, pageUri,
                userContext);

        if (!result.isSuccess()) {
            log.error("[deletePath] projectId={},pageUri={},删除路径配置失败,message={}",
                    projectId, pageUri, result.getMessage());
            throw new BizException("删除路径配置失败: " + result.getMessage());
        }

        log.info("[deletePath] projectId={},pageUri={},删除路径配置成功",
                projectId, pageUri);
        return ReqResult.success(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReqResult<Void> batchConfigProxy(Long projectId, List<ProxyConfig> proxyConfigs,
                                            UserContext userContext) {
        log.info("[batchConfigProxy] projectId={},configCount={},批量配置反向代理", projectId,
                proxyConfigs != null ? proxyConfigs.size() : 0);

        Optional.ofNullable(projectId).filter(x -> x > 0)
                .orElseThrow(() -> new IllegalArgumentException("projectId不能为空或无效"));
        Optional.ofNullable(proxyConfigs)
                .orElseThrow(() -> new IllegalArgumentException("反向代理配置列表不能为空"));

        ReqResult<Void> result = customPageConfigDomainService.batchConfigProxy(projectId, proxyConfigs,
                userContext);

        if (!result.isSuccess()) {
            log.error("[batchConfigProxy] projectId={},configCount={},批量配置反向代理失败,message={}", projectId,
                    proxyConfigs != null ? proxyConfigs.size() : 0, result.getMessage());
            throw new BizException("批量配置反向代理失败: " + result.getMessage());
        }

        log.info("[batchConfigProxy] projectId={},configCount={},批量配置反向代理成功",
                projectId, proxyConfigs != null ? proxyConfigs.size() : 0);
        return ReqResult.success(null);
    }

    public ReqResult<InputStream> exportProjectPublished(Long projectId, UserContext userContext) {
        return customPageConfigDomainService.exportProject(projectId, ExportTypeEnum.PUBLISHED, userContext);
    }

    public ReqResult<InputStream> exportProjectLatest(Long projectId, UserContext userContext) {
        return customPageConfigDomainService.exportProject(projectId, ExportTypeEnum.LATEST, userContext);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReqResult<Void> bindDataSource(Long projectId, String type, Long dataSourceId,
                                          UserContext userContext) {
        log.info("[bindDataSource] projectId={},type={},dataSourceId={},绑定数据源", projectId, type,
                dataSourceId);

        Optional.ofNullable(projectId).filter(x -> x > 0)
                .orElseThrow(() -> new IllegalArgumentException("projectId不能为空或无效"));
        Optional.ofNullable(type).filter(StringUtils::isNotBlank)
                .orElseThrow(() -> new IllegalArgumentException("数据源类型不能为空"));
        Optional.ofNullable(dataSourceId).filter(x -> x > 0)
                .orElseThrow(() -> new IllegalArgumentException("数据源ID不能为空或无效"));

        String dataSourceName = null;
        String dataSourceIcon = null;
        if ("plugin".equalsIgnoreCase(type)) {
            PluginDto pluginDto = pluginApplicationService.queryPublishedPluginConfig(dataSourceId, null);
            if (pluginDto == null) {
                log.error("[bindDataSource] projectId={},type={},dataSourceId={},插件不存在或未发布", projectId, type, dataSourceId);
                return ReqResult.error("0001", "插件不存在或未发布");
            }

            dataSourceName = pluginDto.getName();
            dataSourceIcon = pluginDto.getIcon();
            log.info("[bindDataSource] projectId={},type={},dataSourceId={},获取插件信息成功", projectId, type, dataSourceId);

        } else if ("workflow".equalsIgnoreCase(type)) {
            WorkflowConfigDto workflowConfigDto = workflowApplicationService
                    .queryPublishedWorkflowConfig(dataSourceId, null);
            if (workflowConfigDto == null) {
                log.error("[bindDataSource] projectId={},type={},dataSourceId={},工作流不存在或未发布", projectId, type, dataSourceId);
                return ReqResult.error("0003", "工作流不存在或未发布");
            }

            dataSourceName = workflowConfigDto.getName();
            dataSourceIcon = workflowConfigDto.getIcon();
            log.info("[bindDataSource] projectId={},type={},dataSourceId={},获取工作流信息成功", projectId, type, dataSourceId);

        } else {
            log.error("[bindDataSource] projectId={},type={},dataSourceId={},不支持的数据源类型, type={}", projectId, type, dataSourceId, type);
            return ReqResult.error("0004", "不支持的数据源类型: " + type);
        }

        DataSourceDto dataSource = DataSourceDto.builder()
                .type(type.toLowerCase())
                .id(dataSourceId)
                .key(System.currentTimeMillis() / 1000 + dataSourceId.toString())// 在页面范围内生一个不重复的key
                .name(dataSourceName)
                .icon(dataSourceIcon)
                .build();

        ReqResult<Void> result = customPageConfigDomainService.bindDataSource(projectId, dataSource, userContext);

        if (!result.isSuccess()) {
            log.error("[bindDataSource] 保存数据源失败, projectId={}, type={}, dataSourceId={}, error={}",
                    projectId, type, dataSourceId, result.getMessage());
            throw new BizException("保存数据源失败: " + result.getMessage());
        }

        log.info("[bindDataSource] 保存数据源成功, projectId={}, type={}, dataSourceId={}", projectId, type, dataSourceId);
        return ReqResult.success(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReqResult<Void> unbindDataSource(Long projectId, String type, Long dataSourceId,
                                            UserContext userContext) {
        log.info("[unbindDataSource] projectId={},type={},dataSourceId={},解绑数据源", projectId, type,
                dataSourceId);

        Optional.ofNullable(projectId).filter(x -> x > 0)
                .orElseThrow(() -> new IllegalArgumentException("projectId不能为空或无效"));
        Optional.ofNullable(type).filter(StringUtils::isNotBlank)
                .orElseThrow(() -> new IllegalArgumentException("数据源类型不能为空"));
        Optional.ofNullable(dataSourceId).filter(x -> x > 0)
                .orElseThrow(() -> new IllegalArgumentException("数据源ID不能为空或无效"));

        DataSourceDto dataSource = DataSourceDto.builder()
                .type(type.toLowerCase())
                .id(dataSourceId)
                .build();

        ReqResult<Void> result = customPageConfigDomainService.unbindDataSource(projectId, dataSource, userContext);

        if (!result.isSuccess()) {
            log.error("[unbindDataSource] 解绑数据源失败, projectId={}, type={}, dataSourceId={}, error={}", projectId, type, dataSourceId, result.getMessage());
            throw new BizException("解绑数据源失败: " + result.getMessage());
        }

        log.info("[unbindDataSource] 解绑数据源成功, projectId={}, type={}, dataSourceId={}", projectId, type, dataSourceId);
        return ReqResult.success(null);
    }

    @Override
    public CustomPageConfigModel getByProjectId(Long projectId) {
        log.info("[getByProjectId] projectId={},查询项目", projectId);
        return customPageConfigDomainService.getById(projectId);
    }

    @Override
    public List<CustomPageConfigModel> listByIds(List<Long> ids) {
        return customPageConfigDomainService.listByIds(ids);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReqResult<CustomPageConfigModel> updateProject(CustomPageConfigModel model, UserContext userContext) {
        log.info("[updateProject] projectId={},修改项目", model.getId());
        try {
            Optional.ofNullable(model.getId()).filter(x -> x > 0)
                    .orElseThrow(() -> new IllegalArgumentException("项目ID不能为空或无效"));
            Optional.ofNullable(model.getName()).filter(StringUtils::isNotBlank)
                    .orElseThrow(() -> new IllegalArgumentException("项目名称不能为空"));

            // 更新config表
            ReqResult<CustomPageConfigModel> result = customPageConfigDomainService.update(model,
                    userContext);
            if (!result.isSuccess()) {
                log.error("[updateProject] projectId={},修改项目失败,message={}", model.getId(),
                        result.getMessage());
                throw new BizException("修改项目失败: " + result.getMessage());
            }
            // 更新智能体
            Long devAgentId = ((CustomPageConfigModel) result.getData()).getDevAgentId();
            PageAppAgentUpdateDto agentDto = new PageAppAgentUpdateDto();
            agentDto.setAgentId(devAgentId);
            agentDto.setName(model.getName());
            agentDto.setDescription(model.getDescription());
            agentDto.setIcon(model.getIcon());
            com.xspaceagi.agent.core.sdk.dto.ReqResult<Void> agentResult = agentRpcService
                    .updatePageAppAgent(agentDto);
            if (!agentResult.isSuccess()) {
                log.error("[updateProject] projectId={},更新智能体失败,message={}", model.getId(),
                        agentResult.getMessage());
                throw new BizException("更新智能体失败: " + agentResult.getMessage());
            }

            log.info("[updateProject] projectId={},修改项目成功", model.getId());
            return ReqResult.success(result.getData());
        } catch (Exception e) {
            log.error("[updateProject] projectId={},修改项目异常", model.getId(), e);
            throw new BizException("修改项目异常: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReqResult<Map<String, Object>> deleteProject(Long projectId, UserContext userContext) {
        log.info("[deleteProject] projectId={},删除项目", projectId);
        try {
            Optional.ofNullable(projectId).filter(x -> x > 0)
                    .orElseThrow(() -> new IllegalArgumentException("项目ID不能为空或无效"));

            ReqResult<Map<String, Object>> result = customPageConfigDomainService.delete(projectId, userContext);
            if (!result.isSuccess()) {
                log.error("[deleteProject] projectId={},删除项目失败,message={}", projectId, result.getMessage());
                throw new BizException("删除项目失败: " + result.getMessage());
            }

            // 删除智能体
            try {
                Long devAgentId = ((CustomPageConfigModel) result.getData().get("config"))
                        .getDevAgentId();
                com.xspaceagi.agent.core.sdk.dto.ReqResult<Void> agentResult = agentRpcService
                        .deletePageAppAgent(devAgentId);
                if (!agentResult.isSuccess()) {
                    log.error("[deleteProject] projectId={},删除智能体失败,message={}", projectId,
                            agentResult.getMessage());
                    throw new BizException("删除智能体失败: " + agentResult.getMessage());
                }
            } catch (Exception e) {
                log.error("[deleteProject] projectId={},删除智能体失败", projectId, e);
                // 不抛异常,老数据没有智能体,删除会异常
            }

            log.info("[deleteProject] projectId={},删除项目成功", projectId);
            return result;
        } catch (Exception e) {
            log.error("[deleteProject] projectId={},删除项目异常", projectId, e);
            throw new BizException("删除项目异常: " + e.getMessage());
        }
    }

    @Override
    @DSTransactional(rollbackFor = Exception.class)
    public List<DataSourceDto> copyProjectDataSources(CustomPageConfigModel sourceConfig, CustomPageConfigModel targetConfig, UserContext userContext) {
        List<DataSourceDto> sourceDataSources = sourceConfig.getDataSources();
        if (sourceDataSources == null || sourceDataSources.isEmpty()) {
            return null;
        }

        Long sourceSpaceId = sourceConfig.getSpaceId();
        Long targetProjectId = targetConfig.getId();
        Long targetSpaceId = targetConfig.getSpaceId();
        log.info("[copyProject] targetProjectId={}, targetSpaceId={},开始复制数据源", targetProjectId, targetSpaceId);

        //本空间复制项目
        //直接绑定数据源
        if (sourceSpaceId.equals(targetSpaceId)) {
            log.info("[copyProject] targetProjectId={}, targetSpaceId={},同空间,完全复制绑定关系", targetProjectId, targetSpaceId);
            CustomPageConfigModel updateConfig = new CustomPageConfigModel();
            updateConfig.setId(targetProjectId);
            updateConfig.setDataSources(sourceDataSources);
            customPageConfigDomainService.update(updateConfig, userContext);
            return null;
        }

        //跨空间复制项目

        //新增的数据源
        List<DataSourceDto> newCreateDataSources = new ArrayList<>();
        //目标绑定数据源
        List<DataSourceDto> targetDataSources = new ArrayList<>();

        for (DataSourceDto dataSource : sourceDataSources) {
            Published.TargetType dataSourceType = "plugin".equalsIgnoreCase(dataSource.getType()) ? Published.TargetType.Plugin : Published.TargetType.Workflow;

            //List<PublishedDto> publishedDtos = publishApplicationService.queryPublishedList(dataSourceType, List.of(dataSource.getId()));
            PublishedDto publishedDto = publishApplicationService.queryPublished(dataSourceType, dataSource.getId());

            if (publishedDto == null) {
                log.info("[copyProject] projectId={},dataSourceId={},type={}, 数据源不存在,跳过", targetProjectId, dataSource.getId(), dataSourceType);
                continue;
            }

            if (publishedDto.getScope() == Published.PublishScope.Global || publishedDto.getScope() == Published.PublishScope.Tenant) {
                //全局数据源，不需要复制，直接绑定
                log.info("[copyProject] projectId={},dataSourceId={},type={}, 全局数据源,直接绑定", targetProjectId, dataSource.getId(), dataSourceType);
                targetDataSources.add(dataSource);
            } else if (publishedDto.getPublishedSpaceIds() != null && publishedDto.getPublishedSpaceIds().contains(targetSpaceId)) {
                //已经发布到了目标空间
                log.info("[copyProject] projectId={},dataSourceId={},type={}, 数据源已在目标空间发布过,直接绑定", targetProjectId, dataSource.getId(), dataSourceType);
                targetDataSources.add(dataSource);
            } else {

                if (dataSourceType == Published.TargetType.Plugin) {// 插件
                    //判断是否允许复制
                    boolean allowCopy = false;
                    com.xspaceagi.agent.core.sdk.dto.ReqResult<PluginInfoDto> publishedPluginInfo = agentRpcService.getPublishedPluginInfo(dataSource.getId(), null);
                    if (publishedPluginInfo != null && publishedPluginInfo.isSuccess()) {
                        PluginInfoDto data = publishedPluginInfo.getData();
                        if (sourceConfig.getSpaceId().equals(data.getSpaceId())) {
                            //数据资源和源项目在同空间,复制走
                            allowCopy = true;
                        } else {
                            try {
                                PublishedPermissionDto permissionDto = publishApplicationService.hasPermission(dataSourceType, dataSource.getId());
                                allowCopy = permissionDto.isCopy();
                            } catch (Exception e) {
                                log.info("[copyProject] projectId={},dataSourceId={},type={}, 校验数据源复制权限失败,跳过", targetProjectId, dataSource.getId(), dataSourceType, e);
                            }
                        }
                    }
                    if (!allowCopy) {
                        //不允许复制
                        log.info("[copyProject] projectId={},dataSourceId={},type={}, 无数据源复制权限,跳过", targetProjectId, dataSource.getId(), dataSourceType);
                        continue;
                    }

                    //开始复制
                    com.xspaceagi.agent.core.sdk.dto.ReqResult<String> pluginRes = agentRpcService.queryPluginConfig(dataSource.getId(), null);
                    if (pluginRes != null && pluginRes.isSuccess()) {
                        PluginEnableOrUpdateDto pluginDto = new PluginEnableOrUpdateDto();
                        pluginDto.setUserId(userContext.getUserId());
                        pluginDto.setSpaceId(targetSpaceId);
                        pluginDto.setName(publishedDto.getName());
                        pluginDto.setIcon(publishedDto.getIcon());
                        pluginDto.setConfig(pluginRes.getData());
                        pluginDto.setParamJson("{}");

                        com.xspaceagi.agent.core.sdk.dto.ReqResult<Long> enableResult = agentRpcService.pluginEnableOrUpdate(pluginDto);
                        if (!enableResult.isSuccess()) {
                            log.error("[copyProject] projectId={},dataSourceId={},type={},复制插件失败,message={}", targetProjectId, dataSource.getId(), dataSourceType, enableResult.getMessage());
                            throw new BizException("复制插件失败: " + enableResult.getMessage());
                        } else {
                            log.info("[copyProject] projectId={},dataSourceId={},type={},复制插件成功", targetProjectId, dataSource.getId(), dataSourceType, enableResult.getData());
                        }
                        Long newPluginId = enableResult.getData();
                        DataSourceDto dataSourceDto = DataSourceDto.builder()
                                .id(newPluginId)
                                .type("plugin")
                                .key(dataSource.getKey())
                                .name(publishedDto.getName())
                                .icon(publishedDto.getIcon())
                                .build();
                        targetDataSources.add(dataSourceDto);
                        newCreateDataSources.add(dataSourceDto);
                    }
                } else {
                    // 工作流

                    //判断是否允许复制
                    boolean allowCopy = false;
                    com.xspaceagi.agent.core.sdk.dto.ReqResult<WorkflowInfoDto> publishedWorkflowInfo = agentRpcService.getPublishedWorkflowInfo(dataSource.getId(), null);
                    if (publishedWorkflowInfo != null && publishedWorkflowInfo.isSuccess()) {
                        WorkflowInfoDto data = publishedWorkflowInfo.getData();
                        if (sourceConfig.getSpaceId().equals(data.getSpaceId())) {
                            //数据资源和源项目在同空间,复制走
                            allowCopy = true;
                        } else {
                            try {
                                PublishedPermissionDto permissionDto = publishApplicationService.hasPermission(dataSourceType, dataSource.getId());
                                allowCopy = permissionDto.isCopy();
                            } catch (Exception e) {
                                log.info("[copyProject] projectId={},dataSourceId={},type={}, 校验数据源复制权限失败,跳过", targetProjectId, dataSource.getId(), dataSourceType, e);
                            }
                        }
                    }
                    if (!allowCopy) {
                        //不允许复制
                        log.info("[copyProject] projectId={},dataSourceId={},type={}, 无数据源复制权限,跳过", targetProjectId, dataSource.getId(), dataSourceType);
                        continue;
                    }

                    //开始复制
                    com.xspaceagi.agent.core.sdk.dto.ReqResult<String> workflowRes = agentRpcService.queryTemplateConfig(TargetTypeEnum.Workflow, dataSource.getId());
                    if (workflowRes != null && workflowRes.isSuccess()) {
                        TemplateEnableOrUpdateDto templateDto = new TemplateEnableOrUpdateDto();
                        templateDto.setUserId(userContext.getUserId());
                        templateDto.setTargetType(TargetTypeEnum.Workflow);
                        templateDto.setName(publishedDto.getName());
                        templateDto.setIcon(publishedDto.getIcon());
                        templateDto.setConfig(workflowRes.getData());
                        templateDto.setSpaceId(targetSpaceId);

                        com.xspaceagi.agent.core.sdk.dto.ReqResult<Long> enableResult = agentRpcService.templateEnableOrUpdate(templateDto);
                        if (!enableResult.isSuccess()) {
                            log.error("[copyProject] projectId={},dataSourceId={},type={},复制工作流失败,message={}", targetProjectId, dataSource.getId(), dataSourceType, enableResult.getMessage());
                            throw new BizException("复制工作流失败: " + enableResult.getMessage());
                        } else {
                            log.info("[copyProject] projectId={},dataSourceId={},type={},复制工作流成功", targetProjectId, dataSource.getId(), dataSourceType);
                        }
                        Long newWorkflowId = enableResult.getData();
                        DataSourceDto dataSourceDto = DataSourceDto.builder()
                                .id(newWorkflowId)
                                .type("workflow")
                                .key(dataSource.getKey())
                                .name(publishedDto.getName())
                                .icon(publishedDto.getIcon())
                                .build();
                        targetDataSources.add(dataSourceDto);
                        newCreateDataSources.add(dataSourceDto);
                    }
                }
            }
        }
        if (targetDataSources.isEmpty()) {
            return newCreateDataSources;
        }

        log.info("[copyProject] targetProjectId,targetSpaceId={},跨空间复制,绑定数据源,size={}", targetProjectId, targetSpaceId, targetDataSources.size());
        CustomPageConfigModel updateConfig = new CustomPageConfigModel();
        updateConfig.setId(targetProjectId);
        updateConfig.setDataSources(targetDataSources);
        customPageConfigDomainService.update(updateConfig, userContext);

        return newCreateDataSources;
    }

}
