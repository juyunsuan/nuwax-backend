package com.xspaceagi.custompage.ui.web.controller;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xspaceagi.custompage.application.service.ICustomPageCodingApplicationService;
import com.xspaceagi.custompage.ui.web.dto.PageFilesUpdateReq;
import com.xspaceagi.custompage.ui.web.dto.RollbackVersionReq;
import com.xspaceagi.system.spec.annotation.RequireResource;
import com.xspaceagi.system.spec.common.UserContext;
import com.xspaceagi.system.spec.dto.ReqResult;
import com.xspaceagi.system.spec.exception.SpacePermissionException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

import static com.xspaceagi.system.spec.enums.ResourceEnum.*;

@Tag(name = "网页应用", description = "网页应用相关接口")
@RestController
@RequestMapping("/api/custom-page")
@Slf4j
@RequiredArgsConstructor
public class CustomPageCodingController extends BaseController {

    @Resource
    private ICustomPageCodingApplicationService customPageCodingApplicationService;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @RequireResource(PAGE_APP_MODIFY_FILE)
    @Operation(summary = "指定文件修改", description = "指定文件修改")
    @PostMapping(value = "/specified-files-update", produces = MediaType.APPLICATION_JSON_VALUE)
    public ReqResult<Map<String, Object>> specifiedFilesUpdate(@RequestBody PageFilesUpdateReq req) {
        log.info("[Web] 接收到指定文件修改请求，projectId={}", req.getProjectId());
        try {
            String filesJson = objectMapper.writeValueAsString(req.getFiles());
            log.info("[Web] 文件信息 JSON: {}", filesJson);

            UserContext userContext = getUser();
            return customPageCodingApplicationService.specifiedFilesUpdate(req.getProjectId(), req.getFiles(),
                    userContext);
        } catch (SpacePermissionException e) {
            log.error("[Web] 指定文件修改失败，projectId={}, {}", req.getProjectId(), e.getMessage());
            return ReqResult.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("[Web] 指定文件修改失败，projectId={}", req.getProjectId(), e);
            return ReqResult.error("0001", "指定文件修改失败: " + e.getMessage());
        }
    }

    @RequireResource(PAGE_APP_MODIFY_FILE)
    @Operation(summary = "全量文件修改", description = "全量文件修改")
    @PostMapping(value = "/submit-files-update", produces = MediaType.APPLICATION_JSON_VALUE)
    public ReqResult<Map<String, Object>> submitFilesUpdate(@RequestBody PageFilesUpdateReq req) {
        log.info("[Web] 接收到全量文件修改请求，projectId={}", req.getProjectId());
        try {
            String filesJson = objectMapper.writeValueAsString(req.getFiles());
            log.info("[Web] 文件信息 JSON: {}", filesJson);

            UserContext userContext = getUser();
            return customPageCodingApplicationService.allFilesUpdate(req.getProjectId(), req.getFiles(),
                    userContext);
        } catch (SpacePermissionException e) {
            log.error("[Web] 全量文件修改失败，projectId={}, {}", req.getProjectId(), e.getMessage());
            return ReqResult.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("[Web] 全量文件修改失败，projectId={}", req.getProjectId(), e);
            return ReqResult.error("0001", "全量文件修改失败: " + e.getMessage());
        }
    }

    @RequireResource(PAGE_APP_UPLOAD_FILE)
    @Operation(summary = "上传单个文件", description = "上传单个文件到指定项目路径")
    @PostMapping(value = "/upload-single-file", produces = MediaType.APPLICATION_JSON_VALUE)
    public ReqResult<Map<String, Object>> uploadSingleFile(
            @RequestParam("projectId") Long projectId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("filePath") String filePath) {
        log.info("[Web] 接收到上传单个文件请求，projectId={}, filePath={}", projectId, filePath);
        try {
            if (file == null || file.isEmpty()) {
                return ReqResult.error("0001", "文件不能为空");
            }
            if (filePath == null || filePath.trim().isEmpty()) {
                return ReqResult.error("0001", "文件路径不能为空");
            }

            UserContext userContext = getUser();
            return customPageCodingApplicationService.uploadSingleFile(projectId, file, filePath, userContext);
        } catch (SpacePermissionException e) {
            log.error("[Web] 上传单个文件失败，projectId={}, filePath={}, {}", projectId, filePath, e.getMessage());
            return ReqResult.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("[Web] 上传单个文件失败，projectId={}, filePath={}", projectId, filePath, e);
            return ReqResult.error("0001", "上传单个文件失败: " + e.getMessage());
        }
    }

    @RequireResource(PAGE_APP_QUERY_DETAIL)
    @Operation(summary = "获取文件代理地址", description = "获取单个文件的反向代理地址")
    @GetMapping(value = "/file-proxy-url", produces = MediaType.APPLICATION_JSON_VALUE)
    public ReqResult<String> getFileProxyUrl(
            @RequestParam("projectId") Long projectId,
            @RequestParam("filePath") String filePath) {
        log.info("[Web] 接收到获取文件代理地址请求，projectId={}, filePath={}", projectId, filePath);
        try {
            if (projectId == null || projectId <= 0) {
                return ReqResult.error("0001", "projectId不能为空或无效");
            }
            if (filePath == null || filePath.trim().isEmpty()) {
                return ReqResult.error("0001", "filePath不能为空");
            }

            UserContext userContext = getUser();
            return customPageCodingApplicationService.getFileProxyUrl(projectId, filePath, userContext);
        } catch (SpacePermissionException e) {
            log.error("[Web] 获取文件代理地址失败，projectId={}, filePath={}, {}", projectId, filePath, e.getMessage());
            return ReqResult.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("[Web] 获取文件代理地址失败，projectId={}, filePath={}", projectId, filePath, e);
            return ReqResult.error("0001", "获取文件代理地址失败: " + e.getMessage());
        }
    }

    @RequireResource(PAGE_APP_ROLLBACK_VERSION)
    @Operation(summary = "回滚版本", description = "回滚到指定版本")
    @PostMapping(value = "/rollback-version", produces = MediaType.APPLICATION_JSON_VALUE)
    public ReqResult<Map<String, Object>> rollbackVersion(@RequestBody RollbackVersionReq req) {
        log.info("[Web] 接收到回滚版本请求，projectId={}, rollbackTo={}", req.getProjectId(), req.getRollbackTo());
        try {
            UserContext userContext = getUser();
            return customPageCodingApplicationService.rollbackVersion(req.getProjectId(), req.getRollbackTo(),
                    userContext);
        } catch (SpacePermissionException e) {
            log.error("[Web] 回滚版本失败，projectId={}, rollbackTo={}, {}", req.getProjectId(), req.getRollbackTo(), e.getMessage());
            return ReqResult.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("[Web] 回滚版本失败，projectId={}, rollbackTo={}", req.getProjectId(), req.getRollbackTo(), e);
            return ReqResult.error("0001", "回滚版本失败: " + e.getMessage());
        }
    }

}