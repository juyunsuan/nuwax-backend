package com.xspaceagi.custompage.domain.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.xspaceagi.custompage.domain.gateway.PageFileBuildClient;
import com.xspaceagi.custompage.domain.keepalive.IKeepAliveService;
import com.xspaceagi.custompage.domain.model.CustomPageBuildModel;
import com.xspaceagi.custompage.domain.proxypath.ICustomPageProxyPathService;
import com.xspaceagi.custompage.domain.repository.ICustomPageBuildRepository;
import com.xspaceagi.custompage.domain.repository.ICustomPageConfigRepository;
import com.xspaceagi.custompage.domain.service.ICustomPageBuildDomainService;
import com.xspaceagi.custompage.domain.service.ICustomPageConfigDomainService;
import com.xspaceagi.custompage.sdk.dto.PublishTypeEnum;
import com.xspaceagi.custompage.sdk.dto.VersionInfoDto;
import com.xspaceagi.custompage.sdk.enums.CustomPageActionEnum;
import com.xspaceagi.system.sdk.permission.SpacePermissionService;
import com.xspaceagi.system.spec.common.UserContext;
import com.xspaceagi.system.spec.dto.ReqResult;
import com.xspaceagi.system.spec.enums.ErrorCodeEnum;
import com.xspaceagi.system.spec.enums.YesOrNoEnum;
import com.xspaceagi.system.spec.enums.YnEnum;
import com.xspaceagi.system.spec.page.SuperPage;
import com.xspaceagi.system.spec.utils.DateUtil;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CustomPageBuildDomainServiceImpl implements ICustomPageBuildDomainService {

    @Resource
    private ICustomPageBuildRepository customPageBuildRepository;
    @Resource
    private PageFileBuildClient pageFileBuildClient;
    @Resource
    private IKeepAliveService keepAliveService;
    @Resource
    private ICustomPageConfigRepository customPageConfigRepository;
    @Resource
    private ICustomPageConfigDomainService customPageConfigDomainService;
    @Resource
    private SpacePermissionService spacePermissionService;
    @Resource
    private ICustomPageProxyPathService customPageProxyPathService;

    @Override
    public CustomPageBuildModel getByProjectId(Long projectId) {
        return customPageBuildRepository.getByProjectId(projectId);
    }

    @Override
    public ReqResult<CustomPageBuildModel> createProject(Long projectId, Long spaceId,
            UserContext userContext) throws JsonProcessingException {
        if (spaceId == null) {
            throw new IllegalArgumentException("spaceId不能为空");
        }

        // 不校验空间权限,因为在创建config表的时候已经校验了,这个方法执行一定跟创建config表在同一个事务里
        // spacePermissionService.checkSpaceUserPermission(spaceId);

        CustomPageBuildModel exist = customPageBuildRepository.getByProjectId(projectId);
        if (exist != null) {
            return ReqResult.error("0001", "项目已存在");
        }

        List<VersionInfoDto> versionList = List.of(VersionInfoDto.builder()
                .version(1)
                .time(DateUtil.format(new Date(), "yyyy-MM-dd HH:mm:ss"))
                .action(CustomPageActionEnum.CREATE_PROJECT.getCode())
                .build());

        CustomPageBuildModel model = new CustomPageBuildModel();
        model.setProjectId(projectId);
        model.setDevRunning(YesOrNoEnum.N.getKey());
        model.setBuildRunning(YesOrNoEnum.N.getKey());
        model.setCodeVersion(1);
        model.setVersionInfo(versionList);
        model.setCreatorId(userContext.getUserId());
        model.setCreatorName(userContext.getUserName());
        model.setTenantId(userContext.getTenantId());
        model.setSpaceId(spaceId);
        model.setYn(YnEnum.Y.getKey());

        Long id = customPageBuildRepository.add(model, userContext);
        model.setId(id);

        return ReqResult.success(model);
    }

    // 调用node创建项目
    public ReqResult<Map<String, Object>> initProject(Long projectId) {
        Map<String, Object> resp = pageFileBuildClient.createProject(projectId);
        if (resp == null) {
            return ReqResult.error("9999", "创建项目失败，server端无响应");
        }
        boolean success = Boolean.parseBoolean(String.valueOf(resp.get("success")));
        String message = resp.get("message") == null ? "" : String.valueOf(resp.get("message"));
        if (!success) {
            return ReqResult.error("9999", message);
        }
        return ReqResult.success(resp);
    }

    @Override
    public ReqResult<Map<String, Object>> uploadProject(Long projectId, MultipartFile file, boolean isInitProject,
            UserContext userContext) {
        log.info("[upload-project] projectId={},开始执行", projectId);

        CustomPageBuildModel buildModel = customPageBuildRepository.getByProjectId(projectId);
        if (buildModel == null) {
            return ReqResult.error("0001", "项目构建信息不存在");
        }

        // 校验空间权限
        spacePermissionService.checkSpaceUserPermission(buildModel.getSpaceId());

        // 通过projectId查询devProxyPath
        String devProxyPath = null;
        try {
            devProxyPath = customPageProxyPathService.getDevProxyPath(projectId);
        } catch (Exception e) {
            log.warn("[upload-project] projectId={},没有取到租户默认智能体", projectId);
            // return ReqResult.error("9999", "租户没有配置默认智能体");
        }
        Integer targetVersion = isInitProject ? 1 : (buildModel.getCodeVersion() + 1);

        // 上传
        log.info("[upload-project] projectId={},开始调用server", projectId);
        Map<String, Object> resp = pageFileBuildClient.uploadProject(projectId, file, targetVersion,
                buildModel.getDevPid(), devProxyPath);
        if (resp == null) {
            log.error("[upload-project] projectId={},上传项目失败,server返回null", projectId);
            return ReqResult.error("9999", "上传项目失败，server端无响应");
        }

        boolean success = Boolean.parseBoolean(String.valueOf(resp.get("success")));
        String message = resp.get("message") == null ? "" : String.valueOf(resp.get("message"));
        if (!success) {
            log.error("[upload-project] projectId={},上传项目失败,server返回message={}", projectId, message);
            return ReqResult.error("9999", message);
        }

        // 更新版本信息
        List<VersionInfoDto> versionInfo = isInitProject ? new ArrayList<>() : buildModel.getVersionInfo();
        versionInfo.add(VersionInfoDto.builder()
                .version(targetVersion)
                .time(DateUtil.format(new Date(), "yyyy-MM-dd HH:mm:ss"))
                .action(CustomPageActionEnum.UPLOAD.getCode())
                .build());
        try {
            CustomPageBuildModel updateModel = new CustomPageBuildModel();
            updateModel.setId(buildModel.getId());
            updateModel.setCodeVersion(targetVersion);
            updateModel.setVersionInfo(versionInfo);
            // updateModel.setLastChatModelId(chatModelId);
            // updateModel.setLastMultiModelId(multiModelId);
            customPageBuildRepository.updateVersionInfo(updateModel, userContext);
            log.info("[upload-project] projectId={},更新版本信息成功 targetVersion={}", projectId, targetVersion);
        } catch (Exception e) {
            log.error("[upload-project] projectId={},更新版本信息失败, targetVersion={}", projectId, targetVersion, e);
            // 不抛出异常，因为上传已经成功
        }

        Object pidObj = resp.get("pid");
        Object portObj = resp.get("port");
        if (pidObj == null || portObj == null) {
            log.error("[upload-project] projectId={},dev服务器未启动，message={}", projectId, resp.get("message"));
            keepAliveService.updateKeepAlive(projectId, new Date(), YesOrNoEnum.N.getKey(), null, null, userContext);
            log.warn("[upload-project] projectId={},dev服务器未启动，更新保活信息成功,pid=null,port=null", projectId);
        } else {
            Integer pid = Integer.valueOf(String.valueOf(pidObj));
            Integer port = Integer.valueOf(String.valueOf(portObj));
            keepAliveService.updateKeepAlive(projectId, new Date(), YesOrNoEnum.Y.getKey(), pid, port, userContext);
            log.info("[upload-project] projectId={},dev服务器已启动，更新保活信息成功,pid={},port={}", projectId, pid, port);
        }

        log.info("[upload-project] projectId={},上传项目成功,result={}", projectId, resp);
        return ReqResult.success(resp);

    }

    @Override
    public ReqResult<Map<String, Object>> keepAlive(Long projectId, UserContext userContext) {
        return keepAliveService.handleKeepAlive(projectId, userContext);
    }

    @Override
    public ReqResult<Map<String, Object>> startDev(Long projectId, UserContext userContext) {
        log.info("[startDev] projectId={},开始执行domain", projectId);

        ReqResult<Map<String, Object>> result = keepAliveService.handleKeepAlive(projectId, userContext);
        log.error("[startDev] projectId={},返回结果,result={}", projectId, result);

        return result;
    }

    @Override
    public ReqResult<Map<String, Object>> build(Long projectId, String publishType, UserContext userContext) {
        log.info("[build] projectId={},开始执行domain", projectId);

        CustomPageBuildModel model = customPageBuildRepository.getByProjectId(projectId);
        if (model == null) {
            return ReqResult.error("0001", "项目不存在");
        }
        PublishTypeEnum publishTypeEnum = PublishTypeEnum.getByValue(publishType);
        if (publishTypeEnum == null) {
            return ReqResult.error("0001", "发布类型不存在");
        }

        // 校验空间权限
        spacePermissionService.checkSpaceUserPermission(model.getSpaceId());

        log.error("[build] projectId={},开始调用server", projectId);
        String prodProxyPath = customPageProxyPathService.getProdProxyPath(projectId);
        Map<String, Object> resp = pageFileBuildClient.build(projectId, prodProxyPath);
        if (resp == null) {
            log.error("[build] projectId={},构建失败,server返回null", projectId);
            return ReqResult.error("9999", "构建失败，server端无响应");
        }
        boolean success = Boolean.parseBoolean(String.valueOf(resp.get("success")));
        String message = resp.get("message") == null ? "" : String.valueOf(resp.get("message"));
        if (!success) {
            log.error("[build] projectId={},构建失败,server返回message={}", projectId, message);
            return ReqResult.error("9999", message);
        }
        log.error("[build] projectId={},构建成功", projectId);

        // 更新build表的构建状态
        customPageBuildRepository.updateBuildStatus(projectId, model.getCodeVersion(), userContext);
        log.error("[build] projectId={},build表更新成功", projectId);

        // 更新config表的构建状态
        customPageConfigRepository.updateBuildStatus(projectId, publishTypeEnum, userContext);
        log.error("[build] projectId={},config表更新成功", projectId);

        return ReqResult.success(resp);
    }

    @Override
    public ReqResult<Map<String, Object>> stopDev(Long projectId, UserContext userContext) {
        log.info("[stopDev] projectId={},开始执行domain", projectId);

        CustomPageBuildModel model = customPageBuildRepository.getByProjectId(projectId);
        if (model == null) {
            return ReqResult.error("0001", "项目不存在");
        }

        // 校验空间权限
        spacePermissionService.checkSpaceUserPermission(model.getSpaceId());

        if (model.getDevPid() == null) {
            log.info("[stop-dev] projectId={},从表中查询项目端口为null，不需处理", projectId);
            return ReqResult.success(null);
        }
        if (model.getDevPid() <= 1) {
            log.info("[stop-dev] projectId={},从表中查询项目pid为{},非法,不处理", projectId, model.getDevPid());
            return ReqResult.error("pid异常");
        }

        Map<String, Object> resp = pageFileBuildClient.stopDev(projectId, model.getDevPid());
        if (resp == null) {
            log.error("[stop-dev] projectId={},停止失败,server返回null", projectId);
            return ReqResult.error("9999", "停止失败，server端无响应");
        }
        boolean success = Boolean.parseBoolean(String.valueOf(resp.get("success")));
        String message = resp.get("message") == null ? "" : String.valueOf(resp.get("message"));
        if (!success) {
            log.error("[stop-dev] projectId={},停止失败,server返回message={}", projectId, message);
            return ReqResult.error("9999", message);
        }
        log.info("[stop-dev] projectId={},停止成功", projectId);
        keepAliveService.updateKeepAlive(projectId, new Date(), YesOrNoEnum.N.getKey(), null, null, userContext);
        log.info("[stop-dev] projectId={},保活信息更新成功", projectId);

        return ReqResult.success(resp);
    }

    @Override
    public ReqResult<Map<String, Object>> restartDev(Long projectId, UserContext userContext) {
        log.info("[restartDev] projectId={},开始执行domain", projectId);

        CustomPageBuildModel model = customPageBuildRepository.getByProjectId(projectId);
        if (model == null) {
            return ReqResult.error("0001", "项目不存在");
        }

        // 校验空间权限
        spacePermissionService.checkSpaceUserPermission(model.getSpaceId());

        log.info("[restart-dev] projectId={},开始调用server", projectId);
        Integer pid = model.getDevPid();

        String devProxyPath = customPageProxyPathService.getDevProxyPath(projectId);
        Map<String, Object> resp = pageFileBuildClient.restartDev(projectId, pid, devProxyPath);
        if (resp == null) {
            log.error("[restart-dev] projectId={},重启失败,server返回null", projectId);
            return ReqResult.error("9999", "重启失败，server端无响应");
        }
        boolean success = Boolean.parseBoolean(String.valueOf(resp.get("success")));
        String message = resp.get("message") == null ? "" : String.valueOf(resp.get("message"));
        if (!success) {
            log.error("[restart-dev] projectId={},重启失败,server返回message={}", projectId, message);
            String code = resp.get("code") == null ? "" : String.valueOf(resp.get("code"));
            if ("PROJECT_STARTING".equals(code)) {
                return ReqResult.error(ErrorCodeEnum.PROJECT_STARTING.getCode(), ErrorCodeEnum.PROJECT_STARTING.getMsg());
            }
            return ReqResult.error("9999", message);
        }

        // 重启成功后，更新 dev 运行信息（有则更新，无则插入）
        Integer newPid = null;
        Integer newPort = null;
        try {
            Object pidObj = resp.get("pid");
            Object portObj = resp.get("port");
            newPid = Integer.valueOf(String.valueOf(pidObj));
            newPort = Integer.valueOf(String.valueOf(portObj));
        } catch (Exception e) {
            keepAliveService.updateKeepAlive(projectId, new Date(), YesOrNoEnum.N.getKey(), null, null, userContext);
            log.error("[restart-dev] projectId={},获取开发服务器端口和pid异常,更新保活信息,pid=null,port=null", projectId);

            return ReqResult.error("9999", "获取开发服务器端口和pid异常");
        }
        log.info("[restart-dev] projectId={},重启成功", projectId);
        keepAliveService.updateKeepAlive(projectId, new Date(), 1, newPid, newPort, userContext);
        log.info("[restart-dev] projectId={},更新保活信息成功,pid={},port={}", projectId, newPid, newPort);

        return ReqResult.success(resp);
    }

    @Override
    public SuperPage<CustomPageBuildModel> pageQuery(CustomPageBuildModel model, Long current,
            Long pageSize) {
        return customPageBuildRepository.pageQuery(model, current, pageSize);
    }

    @Override
    public List<CustomPageBuildModel> list(CustomPageBuildModel model) {
        return customPageBuildRepository.list(model);
    }

    @Override
    public List<CustomPageBuildModel> listByProjectIds(List<Long> projectIdList) {
        return customPageBuildRepository.listByProjectIds(projectIdList);
    }

    @Override
    public ReqResult<Map<String, Object>> deleteProjectFiles(CustomPageBuildModel model, UserContext userContext) {
        Long projectId = model.getProjectId();
        log.info("[delete-project-files] projectId={},开始执行domain", projectId);

        // 校验空间权限
        spacePermissionService.checkSpaceUserPermission(model.getSpaceId());

        log.info("[delete-project-files] projectId={},开始调用server", projectId);
        Integer pid = model.getDevPid();

        Map<String, Object> resp = pageFileBuildClient.deleteProject(projectId, pid);
        if (resp == null) {
            log.error("[delete-project-files] projectId={},删除失败,server返回null", projectId);
            return ReqResult.error("9999", "删除失败，server端无响应");
        }
        boolean success = Boolean.parseBoolean(String.valueOf(resp.get("success")));
        String message = resp.get("message") == null ? "" : String.valueOf(resp.get("message"));
        if (!success) {
            log.error("[delete-project-files] projectId={},删除失败,server返回message={}", projectId, message);
            return ReqResult.error("9999", message);
        }

        log.info("[delete-project-files] projectId={},删除成功", projectId);
        return ReqResult.success(resp);
    }

    @Override
    public ReqResult<Map<String, Object>> getDevLog(Long projectId, Integer startIndex, String logType, UserContext userContext) {
        log.debug("[getDevLog] projectId={}, startIndex={}, 开始执行domain", projectId, startIndex);

        CustomPageBuildModel model = customPageBuildRepository.getByProjectId(projectId);
        if (model == null) {
            return ReqResult.error("0001", "项目不存在");
        }

        // 校验空间权限
        spacePermissionService.checkSpaceUserPermission(model.getSpaceId());

        log.debug("[getDevLog] projectId={}, 开始调用server", projectId);

        Map<String, Object> resp = pageFileBuildClient.getDevLog(projectId, startIndex, logType);
        if (resp == null) {
            log.error("[getDevLog] projectId={}, 查询日志失败, server返回null", projectId);
            return ReqResult.error("9999", "查询日志失败，server端无响应");
        }
        boolean success = Boolean.parseBoolean(String.valueOf(resp.get("success")));
        String message = resp.get("message") == null ? "" : String.valueOf(resp.get("message"));
        if (!success) {
            log.error("[getDevLog] projectId={}, 查询日志失败, server返回message={}", projectId, message);
            return ReqResult.error("9999", message);
        }
        log.debug("[getDevLog] projectId={}, 查询日志成功", projectId);

        return ReqResult.success(resp);
    }

    @Override
    public ReqResult<Map<String, Object>> copyProject(Long sourceProjectId, Long targetProjectId) {
        Map<String, Object> resp = pageFileBuildClient.copyProject(sourceProjectId, targetProjectId);
        if (resp == null) {
            return ReqResult.error("9999", "复制项目失败，server端无响应");
        }
        boolean success = Boolean.parseBoolean(String.valueOf(resp.get("success")));
        String message = resp.get("message") == null ? "" : String.valueOf(resp.get("message"));
        if (!success) {
            return ReqResult.error("9999", message);
        }
        return ReqResult.success(resp);
    }

    @Override
    public ReqResult<Map<String, Object>> getLogCacheStats() {
        log.info("[Domain] 获取日志缓存统计");
        Map<String, Object> resp = pageFileBuildClient.getLogCacheStats();
        if (resp == null) {
            return ReqResult.error("9999", "获取日志缓存统计失败，server端无响应");
        }
        boolean success = Boolean.parseBoolean(String.valueOf(resp.get("success")));
        String message = resp.get("message") == null ? "" : String.valueOf(resp.get("message"));
        if (!success) {
            return ReqResult.error("9999", message);
        }
        log.info("[Domain] 获取日志缓存统计成功");
        return ReqResult.success(resp);
    }

    @Override
    public ReqResult<Map<String, Object>> clearAllLogCache() {
        log.info("[Domain] 清理所有日志缓存");
        Map<String, Object> resp = pageFileBuildClient.clearAllLogCache();
        if (resp == null) {
            return ReqResult.error("9999", "清理日志缓存失败，server端无响应");
        }
        boolean success = Boolean.parseBoolean(String.valueOf(resp.get("success")));
        String message = resp.get("message") == null ? "" : String.valueOf(resp.get("message"));
        if (!success) {
            return ReqResult.error("9999", message);
        }
        log.info("[Domain] 清理所有日志缓存成功");
        return ReqResult.success(resp);
    }
}