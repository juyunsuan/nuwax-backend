package com.xspaceagi.custompage.ui.web.controller;

import com.xspaceagi.custompage.application.service.ICustomPageFileApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@Tag(name = "网页应用", description = "网页应用相关接口")
@RestController
@RequestMapping("/api/custom-page")
@Slf4j
@RequiredArgsConstructor
public class CustomPageFileController extends BaseController {

    @Resource
    private ICustomPageFileApplicationService customPageFileApplicationService;

    @Operation(summary = "访问静态文件(page)", description = "访问静态文件(page)")
    @CrossOrigin // 确保 OPTIONS 预检请求被正确处理
//    @GetMapping("/static/{projectId}/**")
    public ResponseEntity<StreamingResponseBody> getStaticFile(@PathVariable("projectId") Long projectId, HttpServletRequest request) {
        log.info("[Web] 接收到访问静态文件请求，projectId={}", projectId);

        if (projectId == null) {
            log.error("[Web] projectId 无效, projectId={}", projectId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        String requestPath = request.getRequestURI();
        String staticPrefix = "/api/custom-page/static/" + projectId + "/";
        String targetPrefix = "/custom-page/static/" + projectId + "/";

        return customPageFileApplicationService.getStaticFile(requestPath, staticPrefix, targetPrefix, String.valueOf(projectId), getUser());
    }

    @Operation(summary = "访问静态文件(page历史版本)", description = "访问静态文件(page历史版本)")
    @CrossOrigin // 确保 OPTIONS 预检请求被正确处理
//    @GetMapping("/static/_his/{projectId}/**")
    public ResponseEntity<StreamingResponseBody> getStaticHisFile(@PathVariable("projectId") Long projectId, HttpServletRequest request) {
        log.info("[Web] 接收到访问静态文件(his)请求，projectId={}", projectId);

        if (projectId == null) {
            log.error("[Web] projectId 无效, projectId={}", projectId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        String requestPath = request.getRequestURI();
        String staticPrefix = "/api/custom-page/static/_his/" + projectId + "/";
        String targetPrefix = "/custom-page/static/_his/" + projectId + "/";

        return customPageFileApplicationService.getStaticFile(requestPath, staticPrefix, targetPrefix, String.valueOf(projectId), getUser());
    }
}