package com.xspaceagi.custompage.ui.web.controller;

import com.xspaceagi.custompage.application.service.ICustomPageBuildApplicationService;
import com.xspaceagi.custompage.domain.proxypath.ICustomPageProxyPathService;
import com.xspaceagi.custompage.ui.web.dto.*;
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

import java.util.List;
import java.util.Map;

import static com.xspaceagi.system.spec.enums.ResourceEnum.*;

@Tag(name = "网页应用", description = "网页应用相关接口")
@RestController
@RequestMapping("/api/custom-page")
@Slf4j
@RequiredArgsConstructor
public class CustomPageBuildController extends BaseController {

    @Resource
    private ICustomPageBuildApplicationService customPageBuildApplicationService;
    @Resource
    private ICustomPageProxyPathService customPageProxyPathApplicationService;

    @RequireResource(PAGE_APP_RESTART_SERVER)
    @Operation(summary = "启动开发服务器", description = "启动前端开发服务器")
    @PostMapping(value = "/start-dev", produces = MediaType.APPLICATION_JSON_VALUE)
    public ReqResult<CustomBuildRes> startDev(@RequestBody CustomBuildReq req) {
        log.info("[Web] 接收到启动前端服务请求，projectId={}", req.getProjectId());
        try {
            UserContext userContext = getUser();

            ReqResult<Map<String, Object>> result = customPageBuildApplicationService.startDev(req.getProjectId(),
                    userContext);
            log.info("[Web] 启动前端服务完成，返回码={}", result.getCode());

            if (!result.isSuccess()) {
                return ReqResult.create(result.getCode(), result.getMessage(), null);
            }

            CustomBuildRes res = new CustomBuildRes();
            res.setProjectId(req.getProjectId());
            res.setProjectIdStr(String.valueOf(req.getProjectId()));

            Map<String, Object> data = result.getData();
            if (data != null) {
                Object portObj = data.get("port");
                if (portObj != null) {
                    res.setDevServerUrl(customPageProxyPathApplicationService.getDevProxyPath(req.getProjectId()));
                }
            }
            return ReqResult.success(res);
        } catch (SpacePermissionException e) {
            log.error("[Web] 启动前端服务失败，projectId={}, {}", req.getProjectId(), e.getMessage());
            return ReqResult.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("[Web] 启动前端服务失败， projectId={}", req.getProjectId(), e);
            return ReqResult.error("0001", "启动前端服务失败: " + e.getMessage());
        }
    }

    @RequireResource(PAGE_APP_PUBLISH)
    @Operation(summary = "构建并发布项目", description = "构建并发布前端项目")
    @PostMapping(value = "/build", produces = MediaType.APPLICATION_JSON_VALUE)
    public ReqResult<CustomBuildRes> build(@RequestBody CustomBuildReq req) {
        log.info("[Web] 接收到构建发布请求，projectId={}", req.getProjectId());
        try {
            UserContext userContext = getUser();

            ReqResult<Map<String, Object>> result = customPageBuildApplicationService.build(req.getProjectId(),
                    req.getPublishType(), userContext);
            log.info("[Web] 构建发布完成，返回码={}", result.getCode());

            if (!result.isSuccess()) {
                return ReqResult.create(result.getCode(), result.getMessage(), null);
            }

            CustomBuildRes res = new CustomBuildRes();
            res.setProjectId(req.getProjectId());
            res.setProjectIdStr(String.valueOf(req.getProjectId()));
            res.setProdServerUrl(customPageProxyPathApplicationService.getProdProxyPath(req.getProjectId()));

            return ReqResult.success(res);
        } catch (SpacePermissionException e) {
            log.error("[Web] 构建发布失败，projectId={}, {}", req.getProjectId(), e.getMessage());
            return ReqResult.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("[Web] 构建发布失败，projectId={}", req.getProjectId(), e);
            return ReqResult.error("0001", "构建发布失败: " + e.getMessage());
        }
    }

    @RequireResource(PAGE_APP_RESTART_SERVER)
    @Operation(summary = "停止开发服务器", description = "停止前端开发服务器")
    @PostMapping(value = "/stop-dev", produces = MediaType.APPLICATION_JSON_VALUE)
    public ReqResult<CustomBuildRes> stopDev(@RequestBody CustomBuildReq req) {
        log.info("[Web] 接收到停止开发服务器请求，projectName={}", req.getProjectId());
        try {
            UserContext userContext = getUser();

            ReqResult<Map<String, Object>> result = customPageBuildApplicationService.stopDev(req.getProjectId(),
                    userContext);
            log.info("[Web] 停止开发服务器完成，返回码={}", result.getCode());

            if (!result.isSuccess()) {
                return ReqResult.create(result.getCode(), result.getMessage(), null);
            }

            CustomBuildRes res = new CustomBuildRes();
            res.setProjectId(req.getProjectId());
            res.setProjectIdStr(String.valueOf(req.getProjectId()));
            return ReqResult.success(res);
        } catch (SpacePermissionException e) {
            log.error("[Web] 停止开发服务器失败，projectName={}, {}", req.getProjectId(), e.getMessage());
            return ReqResult.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("[Web] 停止开发服务器失败，projectName={}", req.getProjectId(), e);
            return ReqResult.error("0001", "停止开发服务器失败: " + e.getMessage());
        }
    }

    @RequireResource(PAGE_APP_RESTART_SERVER)
    @Operation(summary = "重启开发服务器", description = "重启前端开发服务器")
    @PostMapping(value = "/restart-dev", produces = MediaType.APPLICATION_JSON_VALUE)
    public ReqResult<CustomBuildRes> restartDev(@RequestBody CustomBuildReq req) {
        log.info("[Web] 接收到重启开发服务器请求，projectId={}", req.getProjectId());
        try {
            UserContext userContext = getUser();

            ReqResult<Map<String, Object>> result = customPageBuildApplicationService.restartDev(req.getProjectId(),
                    userContext);
            log.info("[Web] 重启开发服务器完成，返回码={}", result.getCode());

            if (!result.isSuccess()) {
                return ReqResult.create(result.getCode(), result.getMessage(), null);
            }

            CustomBuildRes res = new CustomBuildRes();
            res.setProjectId(req.getProjectId());
            res.setProjectIdStr(String.valueOf(req.getProjectId()));

            Map<String, Object> data = result.getData();
            if (data != null) {
                Object portObj = data.get("port");
                if (portObj != null) {
                    res.setDevServerUrl(customPageProxyPathApplicationService.getDevProxyPath(req.getProjectId()));
                }
            }
            return ReqResult.success(res);
        } catch (SpacePermissionException e) {
            log.error("[Web] 重启开发服务器失败，projectId={}, {}", req.getProjectId(), e.getMessage());
            return ReqResult.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("[Web] 重启开发服务器失败，projectId={}", req.getProjectId(), e);
            return ReqResult.error("0001", "重启开发服务器失败: " + e.getMessage());
        }
    }

    @RequireResource(PAGE_APP_QUERY_DETAIL)
    @Operation(summary = "开发服务器保活", description = "前端定时请求后端保活，超时无请求则自动停止开发服务器")
    @PostMapping(value = "/keepalive", produces = MediaType.APPLICATION_JSON_VALUE)
    public ReqResult<KeepAliveRes> keepAlive(@RequestBody KeepAliveReq req) {
        log.debug("[Web] projectId={},收到保活请求", req.getProjectId());
        try {
            UserContext userContext = getUser();

            ReqResult<Map<String, Object>> result = customPageBuildApplicationService.keepAlive(req.getProjectId(), userContext);
            log.debug("[Web] projectId={},保活处理完成，返回码={}", req.getProjectId(), result.getCode());

            if (!result.isSuccess()) {
                return ReqResult.create(result.getCode(), result.getMessage(), null);
            }

            KeepAliveRes res = new KeepAliveRes();
            res.setProjectId(req.getProjectId());
            res.setProjectIdStr(String.valueOf(req.getProjectId()));

            Map<String, Object> data = result.getData();
            if (data != null) {
                Object portObj = data.get("port");
                if (portObj != null) {
                    res.setDevServerUrl(customPageProxyPathApplicationService.getDevProxyPath(req.getProjectId()));
                }
            }
            return ReqResult.success(res);
        } catch (SpacePermissionException e) {
            log.error("[Web] projectId={},保活处理失败, {}", req.getProjectId(), e.getMessage());
            return ReqResult.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("[Web] projectId={},保活处理异常", req.getProjectId(), e);
            return ReqResult.error("0001", e.getMessage());
        }
    }

    @RequireResource(PAGE_APP_QUERY_DETAIL)
    @Operation(summary = "查询开发服务器日志", description = "查询前端开发服务器日志")
    @PostMapping(value = "/get-dev-log", produces = MediaType.APPLICATION_JSON_VALUE)
    public ReqResult<GetDevLogRes> getDevLog(@RequestBody GetDevLogReq req) {
        log.debug("[getDevLog] projectId={}, 接收到请求，startIndex={},logType={}", req.getProjectId(), req.getStartIndex(), req.getLogType());
        try {
            UserContext userContext = getUser();

            if (req.getLogType() == null) {
                req.setLogType("temp");
            }
            ReqResult<Map<String, Object>> result = customPageBuildApplicationService.getDevLog(req.getProjectId(),
                    req.getStartIndex(), req.getLogType(), userContext);
            log.debug("[getDevLog] 查询完成，返回码={}", result.getCode());

            if (!result.isSuccess()) {
                return ReqResult.create(result.getCode(), result.getMessage(), null);
            }

            GetDevLogRes res = new GetDevLogRes();
            res.setProjectId(req.getProjectId());
            res.setStartIndex(req.getStartIndex());

            Map<String, Object> data = result.getData();
            if (data != null) {
                Object logsObj = data.get("logs");
                Object totalLinesObj = data.get("totalLines");
                Object cacheHitObj = data.get("cacheHit");
                Object fileTooLargeObj = data.get("fileTooLarge");
                if (logsObj != null) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> logsList = (List<Map<String, Object>>) logsObj;
                    res.setLogs(logsList);
                }
                if (totalLinesObj != null) {
                    res.setTotalLines((Integer) totalLinesObj);
                }
                if (cacheHitObj != null) {
                    res.setCacheHit(Boolean.TRUE.equals(cacheHitObj));
                }
                if (fileTooLargeObj != null) {
                    res.setFileTooLarge(Boolean.TRUE.equals(fileTooLargeObj));
                }
                res.setLogFileName(data.get("logFileName") == null ? null : data.get("logFileName").toString());
            }
            return ReqResult.success(res);
        } catch (SpacePermissionException e) {
            log.error("[getDevLog] projectId={}, 查询日志失败，{}", req.getProjectId(), e.getMessage());
            return ReqResult.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("[getDevLog] projectId={}, 查询日志失败", req.getProjectId(), e);
            return ReqResult.error("0001", "查询日志失败: " + e.getMessage());
        }
    }

    @RequireResource(PAGE_APP_QUERY_LIST)
    @Operation(summary = "获取日志缓存统计", description = "获取所有项目的日志缓存统计信息")
    @GetMapping(value = "/get-log-cache-stats", produces = MediaType.APPLICATION_JSON_VALUE)
    public ReqResult<Map<String, Object>> getLogCacheStats() {
        log.info("[Web] 接收到获取日志缓存统计请求");
        try {
            ReqResult<Map<String, Object>> result = customPageBuildApplicationService.getLogCacheStats();
            log.info("[Web] 获取日志缓存统计完成，返回码={}", result.getCode());

            if (!result.isSuccess()) {
                return ReqResult.create(result.getCode(), result.getMessage(), null);
            }

            return ReqResult.success(result.getData());
        } catch (Exception e) {
            log.error("[Web] 获取日志缓存统计失败", e);
            return ReqResult.error("0001", "获取日志缓存统计失败: " + e.getMessage());
        }
    }

    @RequireResource(PAGE_APP_QUERY_LIST)
    @Operation(summary = "清理日志缓存", description = "清理所有项目的日志缓存")
    @GetMapping(value = "/clear-all-log-cache", produces = MediaType.APPLICATION_JSON_VALUE)
    public ReqResult<Map<String, Object>> clearAllLogCache() {
        log.info("[Web] 接收到清理日志缓存请求");
        try {
            ReqResult<Map<String, Object>> result = customPageBuildApplicationService.clearAllLogCache();
            log.info("[Web] 清理日志缓存完成，返回码={}", result.getCode());

            if (!result.isSuccess()) {
                return ReqResult.create(result.getCode(), result.getMessage(), null);
            }

            return ReqResult.success(result.getData());
        } catch (Exception e) {
            log.error("[Web] 清理日志缓存失败", e);
            return ReqResult.error("0001", "清理日志缓存失败: " + e.getMessage());
        }
    }

}