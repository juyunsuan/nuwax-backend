package com.xspaceagi.custompage.domain.service.impl;

import com.xspaceagi.custompage.domain.dto.PageFileInfo;
import com.xspaceagi.custompage.domain.gateway.PageFileBuildClient;
import com.xspaceagi.custompage.domain.keepalive.IKeepAliveService;
import com.xspaceagi.custompage.domain.model.CustomPageBuildModel;
import com.xspaceagi.custompage.domain.proxypath.ICustomPageProxyPathService;
import com.xspaceagi.custompage.domain.repository.ICustomPageBuildRepository;
import com.xspaceagi.custompage.domain.service.ICustomPageCodingDomainService;
import com.xspaceagi.custompage.sdk.dto.VersionInfoDto;
import com.xspaceagi.custompage.sdk.enums.CustomPageActionEnum;
import com.xspaceagi.system.sdk.permission.SpacePermissionService;
import com.xspaceagi.system.spec.common.UserContext;
import com.xspaceagi.system.spec.dto.ReqResult;
import com.xspaceagi.system.spec.utils.DateUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.groovy.util.Maps;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class CustomPageCodingDomainServiceImpl implements ICustomPageCodingDomainService {

    @Resource
    private IKeepAliveService keepAliveService;
    @Resource
    private PageFileBuildClient pageFileBuildClient;
    @Resource
    private SpacePermissionService spacePermissionService;
    @Resource
    private ICustomPageBuildRepository customPageBuildRepository;
    @Resource
    private ICustomPageProxyPathService customPageProxyPathService;

    @Override
    public ReqResult<Map<String, Object>> specifiedFilesUpdate(Long projectId, List<PageFileInfo> files,
                                                               UserContext userContext) {
        log.info("[specifiedFilesUpdate] projectId={},开始执行domain", projectId);
        CustomPageBuildModel buildModel = customPageBuildRepository.getByProjectId(projectId);
        if (buildModel == null) {
            return ReqResult.error("0001", "项目不存在");
        }

        // 校验空间权限
        spacePermissionService.checkSpaceUserPermission(buildModel.getSpaceId());


        String devProxyPath = customPageProxyPathService.getDevProxyPath(projectId);
        Map<String, Object> resp = pageFileBuildClient.specifiedFilesUpdate(projectId, files, buildModel.getCodeVersion(), devProxyPath,
                buildModel.getDevPid());
        if (resp == null) {
            return ReqResult.error("9999", "指定文件修改失败，server端无响应");
        }
        boolean success = Boolean.parseBoolean(String.valueOf(resp.get("success")));
        String message = resp.get("message") == null ? "" : String.valueOf(resp.get("message"));
        if (!success) {
            return ReqResult.error("9999", message);
        }

        // 更新版本信息
        VersionInfoDto newVersionInfo = VersionInfoDto.builder()
                .action(CustomPageActionEnum.SUBMIT_FILES_UPDATE.getCode())
                .build();
        updateVersion(buildModel, newVersionInfo, userContext);

        return ReqResult.success(resp);
    }

    @Override
    public ReqResult<Map<String, Object>> allFilesUpdate(Long projectId, List<PageFileInfo> files,
                                                         UserContext userContext) {
        log.info("[allFilesUpdate] projectId={},开始执行domain", projectId);
        CustomPageBuildModel buildModel = customPageBuildRepository.getByProjectId(projectId);
        if (buildModel == null) {
            return ReqResult.error("0001", "项目不存在");
        }

        // 校验空间权限
        spacePermissionService.checkSpaceUserPermission(buildModel.getSpaceId());

        String devProxyPath = customPageProxyPathService.getDevProxyPath(projectId);
        Map<String, Object> resp = pageFileBuildClient.allFilesUpdate(projectId, files, buildModel.getCodeVersion(), devProxyPath,
                buildModel.getDevPid());
        if (resp == null) {
            return ReqResult.error("9999", "全量文件修改失败，server端无响应");
        }
        boolean success = Boolean.parseBoolean(String.valueOf(resp.get("success")));
        String message = resp.get("message") == null ? "" : String.valueOf(resp.get("message"));
        if (!success) {
            return ReqResult.error("9999", message);
        }

        // 更新版本信息
        VersionInfoDto newVersionInfo = VersionInfoDto.builder()
                .action(CustomPageActionEnum.SUBMIT_FILES_UPDATE.getCode())
                .build();
        updateVersion(buildModel, newVersionInfo, userContext);

        // 开发服务器可能重启,如果重启则更新
        Object pidObj = resp.get("pid");
        Object portObj = resp.get("port");
        if (pidObj instanceof Integer && portObj instanceof Integer) {
            Integer pid = Integer.valueOf(String.valueOf(pidObj));
            Integer port = Integer.valueOf(String.valueOf(portObj));
            keepAliveService.updateKeepAlive(projectId, new Date(), 1, pid, port, userContext);
        }

        return ReqResult.success(resp);
    }

    @Override
    public ReqResult<Map<String, Object>> uploadSingleFile(Long projectId, MultipartFile file, String filePath,
                                                           UserContext userContext) {
        log.info("[uploadSingleFile] projectId={},开始执行domain", projectId);

        CustomPageBuildModel buildModel = customPageBuildRepository.getByProjectId(projectId);
        if (buildModel == null) {
            return ReqResult.error("0001", "项目不存在");
        }

        // 校验空间权限
        spacePermissionService.checkSpaceUserPermission(buildModel.getSpaceId());

        Integer currentVersion = buildModel.getCodeVersion() == null ? 0 : buildModel.getCodeVersion();

        Map<String, Object> resp = pageFileBuildClient.uploadSingleFile(projectId, file, filePath, currentVersion);
        if (resp == null) {
            return ReqResult.error("9999", "上传文件失败，server端无响应");
        }
        boolean success = Boolean.parseBoolean(String.valueOf(resp.get("success")));
        String message = resp.get("message") == null ? "" : String.valueOf(resp.get("message"));
        if (!success) {
            return ReqResult.error("9999", message);
        }

        // 更新版本信息
        VersionInfoDto newVersionInfo = VersionInfoDto.builder()
                .action(CustomPageActionEnum.UPLOAD_SINGLE_FILE.getCode())
                .ext(Maps.of("filePath", filePath))
                .build();
        updateVersion(buildModel, newVersionInfo, userContext);

        // 开发服务器可能重启,如果重启则更新updateKeepAlive
        Object pidObj = resp.get("pid");
        Object portObj = resp.get("port");
        if (pidObj instanceof Integer && portObj instanceof Integer) {
            Integer pid = Integer.valueOf(String.valueOf(pidObj));
            Integer port = Integer.valueOf(String.valueOf(portObj));
            keepAliveService.updateKeepAlive(projectId, new Date(), 1, pid, port, userContext);
        }

        return ReqResult.success(resp);
    }

    @Override
    public ReqResult<Map<String, Object>> rollbackVersion(Long projectId, Integer rollbackTo,
                                                          UserContext userContext) {
        log.info("[rollbackToVersion] projectId={},开始执行domain,rollbackToVersion={}", projectId, rollbackTo);
        CustomPageBuildModel buildModel = customPageBuildRepository.getByProjectId(projectId);
        if (buildModel == null) {
            return ReqResult.error("0001", "项目不存在");
        }

        // 校验空间权限
        spacePermissionService.checkSpaceUserPermission(buildModel.getSpaceId());

        // 校验版本号
        Integer currentVersion = buildModel.getCodeVersion() == null ? 0 : buildModel.getCodeVersion();
        if (rollbackTo >= currentVersion) {
            return ReqResult.error("0002", "回滚版本号必须小于当前版本号");
        }
        if (rollbackTo < 1) {
            return ReqResult.error("0002", "回滚版本号不能小于1");
        }

        String devProxyPath = customPageProxyPathService.getDevProxyPath(projectId);
        Map<String, Object> resp = pageFileBuildClient.rollbackVersion(projectId, rollbackTo, buildModel.getCodeVersion(), devProxyPath,
                buildModel.getDevPid());
        if (resp == null) {
            return ReqResult.error("9999", "回滚版本失败，server端无响应");
        }
        boolean success = Boolean.parseBoolean(String.valueOf(resp.get("success")));
        String message = resp.get("message") == null ? "" : String.valueOf(resp.get("message"));
        if (!success) {
            return ReqResult.error("9999", message);
        }

        // 更新版本信息
        VersionInfoDto newVersionInfo = VersionInfoDto.builder()
                .action(CustomPageActionEnum.ROLLBACK_VERSION.getCode())
                .ext(Maps.of("rollbackTo", String.valueOf(rollbackTo)))
                .build();
        updateVersion(buildModel, newVersionInfo, userContext);

        // 开发服务器可能重启,如果重启则更新
        Object pidObj = resp.get("pid");
        Object portObj = resp.get("port");
        if (pidObj instanceof Integer && portObj instanceof Integer) {
            Integer pid = Integer.valueOf(String.valueOf(pidObj));
            Integer port = Integer.valueOf(String.valueOf(portObj));
            keepAliveService.updateKeepAlive(projectId, new Date(), 1, pid, port, userContext);
        }

        return ReqResult.success(resp);
    }

    // 更新版本信息
    private void updateVersion(CustomPageBuildModel buildModel, VersionInfoDto newVersionInfo, UserContext userContext) {
        Integer nextVersion = buildModel.getCodeVersion() + 1;

        List<VersionInfoDto> versionInfo = buildModel.getVersionInfo();
        newVersionInfo.setVersion(nextVersion);
        newVersionInfo.setTime(DateUtil.format(new Date(), "yyyy-MM-dd HH:mm:ss"));
        versionInfo.add(newVersionInfo);

        CustomPageBuildModel updateModel = new CustomPageBuildModel();
        updateModel.setId(buildModel.getId());
        updateModel.setCodeVersion(nextVersion);
        updateModel.setVersionInfo(versionInfo);
        customPageBuildRepository.updateVersionInfo(updateModel, userContext);
    }

}
