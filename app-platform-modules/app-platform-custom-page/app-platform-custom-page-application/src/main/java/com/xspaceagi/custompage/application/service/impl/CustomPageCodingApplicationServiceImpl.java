package com.xspaceagi.custompage.application.service.impl;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.xspaceagi.custompage.application.service.ICustomPageCodingApplicationService;
import com.xspaceagi.custompage.domain.dto.PageFileInfo;
import com.xspaceagi.custompage.domain.proxypath.ICustomPageProxyPathService;
import com.xspaceagi.custompage.domain.service.ICustomPageChatDomainService;
import com.xspaceagi.custompage.domain.service.ICustomPageCodingDomainService;
import com.xspaceagi.custompage.domain.service.ICustomPageConversationDomainService;
import com.xspaceagi.system.spec.common.UserContext;
import com.xspaceagi.system.spec.dto.ReqResult;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CustomPageCodingApplicationServiceImpl implements ICustomPageCodingApplicationService {

    @Resource
    private ICustomPageChatDomainService customPageChatDomainService;
    @Resource
    private ICustomPageCodingDomainService customPageCodingDomainService;
    @Resource
    private ICustomPageProxyPathService customPageProxyPathApplicationService;
    @Resource
    private ICustomPageConversationDomainService customPageConversationDomainService;

    @Override
    public ReqResult<Map<String, Object>> specifiedFilesUpdate(Long projectId, List<PageFileInfo> files, UserContext userContext) {
        log.info("[Application] projectId={},指定文件修改", projectId);
        Optional.ofNullable(projectId).filter(x -> x > 0)
                .orElseThrow(() -> new IllegalArgumentException("projectId不能为空或无效"));
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("files不能为空");
        }

        ReqResult<Map<String, Object>> result = customPageCodingDomainService.specifiedFilesUpdate(projectId, files,
                userContext);

        log.info("[Application] projectId={},指定文件修改结束,result={}", projectId, result);
        return result;
    }

    // 全量文件修改,不开启事务,如果保活信息更新失败,不要影响版本信息
    // @Transactional(rollbackFor = Exception.class)
    @Override
    public ReqResult<Map<String, Object>> allFilesUpdate(Long projectId, List<PageFileInfo> files,
                                                         UserContext userContext) {
        log.info("[Application] projectId={},全量文件修改", projectId);
        Optional.ofNullable(projectId).filter(x -> x > 0)
                .orElseThrow(() -> new IllegalArgumentException("projectId不能为空或无效"));
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("files不能为空");
        }

        ReqResult<Map<String, Object>> result = customPageCodingDomainService.allFilesUpdate(projectId, files,
                userContext);

        log.info("[Application] projectId={},全量文件修改结束,result={}", projectId, result);
        return result;
    }

    // 上传文件,不开启事务,如果保活信息更新失败,不要影响版本信息
    // @Transactional(rollbackFor = Exception.class)
    @Override
    public ReqResult<Map<String, Object>> uploadSingleFile(Long projectId, MultipartFile file, String filePath,
                                                           UserContext userContext) {
        log.info("[Application] projectId={},上传单个文件,filePath={}", projectId, filePath);
        Optional.ofNullable(projectId).filter(x -> x > 0)
                .orElseThrow(() -> new IllegalArgumentException("projectId不能为空或无效"));
        Optional.ofNullable(file).orElseThrow(() -> new IllegalArgumentException("file不能为空"));
        Optional.ofNullable(filePath).filter(x -> !x.trim().isEmpty())
                .orElseThrow(() -> new IllegalArgumentException("filePath不能为空"));

        ReqResult<Map<String, Object>> result = customPageCodingDomainService.uploadSingleFile(projectId, file,
                filePath, userContext);
        log.info("[Application] projectId={},上传单个文件结束,result={}", projectId, result);
        return result;
    }

    @Override
    public ReqResult<String> getFileProxyUrl(Long projectId, String filePath, UserContext userContext) {
        log.info("[Application] projectId={},获取文件代理地址,filePath={}", projectId, filePath);
        Optional.ofNullable(projectId).filter(x -> x > 0)
                .orElseThrow(() -> new IllegalArgumentException("projectId不能为空或无效"));
        Optional.ofNullable(filePath).filter(x -> !x.trim().isEmpty())
                .orElseThrow(() -> new IllegalArgumentException("filePath不能为空"));

        try {
            // 获取开发环境代理路径
            String devProxyPath = customPageProxyPathApplicationService.getDevProxyPath(projectId);
            // 去掉filePath开头的所有/
            String normalizedFilePath = filePath.replaceAll("^/+", "");
            String devProxyUrl = devProxyPath + normalizedFilePath;

            log.info("[Application] projectId={},获取文件代理地址响应,proxyUrl={}", devProxyUrl);
            return ReqResult.success(devProxyUrl);
        } catch (Exception e) {
            log.error("[Application] projectId={},获取文件代理地址异常,filePath={}", projectId, filePath, e);
            return ReqResult.error("0001", "获取文件代理地址异常: " + e.getMessage());
        }
    }

    @Override
    public ReqResult<Map<String, Object>> rollbackVersion(Long projectId, Integer rollbackTo,
            UserContext userContext) {
        log.info("[Application] projectId={},回滚版本,rollbackTo={}", projectId, rollbackTo);
        Optional.ofNullable(projectId).filter(x -> x > 0)
                .orElseThrow(() -> new IllegalArgumentException("projectId不能为空或无效"));
        Optional.ofNullable(rollbackTo).filter(x -> x >= 0)
                .orElseThrow(() -> new IllegalArgumentException("rollbackTo不能为空或无效"));

        ReqResult<Map<String, Object>> result = customPageCodingDomainService.rollbackVersion(projectId,
                rollbackTo, userContext);

        log.info("[Application] projectId={},回滚版本结束,result={}", projectId, result);
        return result;
    }

}