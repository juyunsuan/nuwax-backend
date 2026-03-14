package com.xspaceagi.custompage.domain.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xspaceagi.custompage.domain.dto.PageFileInfo;
import com.xspaceagi.custompage.sdk.dto.ProjectConfigExportDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class PageFileBuildClient {

    @Value("${custom-page.build-server.base-url}")
    private String baseUrl;

    private final WebClient webClient = WebClient.builder().build();

    public Map<String, Object> startDev(Long projectId, String devProxyPath) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/build/start-dev").queryParam("projectId", projectId).queryParam("basePath", devProxyPath).toUriString();
        log.info("[Build-server] projectId={} 调用开发启动接口, url={}", projectId, url);

        RestTemplate restTemplate = new RestTemplate();
        try {
            ResponseEntity<Map<String, Object>> entity = restTemplate.exchange(url, HttpMethod.GET, null, new ParameterizedTypeReference<Map<String, Object>>() {
            });
            Map<String, Object> body = entity.getBody();
            log.info("[Build-server] projectId={} 调用开发启动接口, 响应结果={}", projectId, body);
            return body;
        } catch (HttpClientErrorException e) {
            log.warn("[Build-server] projectId={} 调用开发启动接口失败, status={}, responseBody={}", projectId, e.getStatusCode(), e.getResponseBodyAsString());
            return parseClientErr(projectId, e);
        }
    }

    public Map<String, Object> keepAlive(Long projectId, String devProxyPath, Integer devPid, Integer devPort) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/build/keep-alive").queryParam("projectId", projectId).queryParam("basePath", devProxyPath).queryParam("pid", devPid).queryParam("port", devPort).toUriString();
        log.info("[Build-server] projectId={} 调用保活接口, url={}", projectId, url);

        RestTemplate restTemplate = new RestTemplate();
        try {
            ResponseEntity<Map<String, Object>> entity = restTemplate.exchange(url, HttpMethod.GET, null, new ParameterizedTypeReference<Map<String, Object>>() {
            });
            Map<String, Object> body = entity.getBody();
            log.info("[Build-server] projectId={} 调用保活接口, 响应结果={}", projectId, body);
            return body;
        } catch (HttpClientErrorException e) {
            log.warn("[Build-server] projectId={} 调用保活接口失败, status={}, responseBody={}", projectId, e.getStatusCode(), e.getResponseBodyAsString());
            return parseClientErr(projectId, e);
        }
    }

    public Map<String, Object> build(Long projectId, String prodProxyPath) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/build/build").queryParam("projectId", projectId).queryParam("basePath", prodProxyPath).toUriString();
        log.info("[Build-server] projectId={} 调用构建接口, url={}", projectId, url);

        RestTemplate restTemplate = new RestTemplate();
        try {
            ResponseEntity<Map<String, Object>> entity = restTemplate.exchange(url, HttpMethod.GET, null, new ParameterizedTypeReference<Map<String, Object>>() {
            });
            Map<String, Object> body = entity.getBody();
            log.info("[Build-server] projectId={} 调用构建接口, 响应结果={}", projectId, body);
            return body;
        } catch (HttpClientErrorException e) {
            log.warn("[Build-server] projectId={} 调用构建接口失败, status={}, responseBody={}", projectId, e.getStatusCode(), e.getResponseBodyAsString());
            return parseClientErr(projectId, e);
        }
    }

    public Map<String, Object> stopDev(Long projectId, Integer pid) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/build/stop-dev").queryParam("projectId", projectId).queryParam("pid", pid).toUriString();
        log.info("[Build-server] projectId={} 调用停止开发服务器接口, url={}", projectId, url);

        RestTemplate restTemplate = new RestTemplate();
        try {
            ResponseEntity<Map<String, Object>> entity = restTemplate.exchange(url, HttpMethod.GET, null, new ParameterizedTypeReference<Map<String, Object>>() {
            });
            Map<String, Object> body = entity.getBody();
            log.info("[Build-server] projectId={} 调用停止开发服务器接口, 响应结果={}", projectId, body);
            return body;
        } catch (HttpClientErrorException e) {
            log.warn("[Build-server] projectId={} 调用停止开发服务器接口失败, status={}, responseBody={}", projectId, e.getStatusCode(), e.getResponseBodyAsString());
            return parseClientErr(projectId, e);
        }
    }

    public Map<String, Object> restartDev(Long projectId, Integer pid, String devProxyPath) {
        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/build/restart-dev").queryParam("projectId", projectId).queryParam("basePath", devProxyPath);

        if (pid != null) {
            uriComponentsBuilder.queryParam("pid", pid);
        }
        String url = uriComponentsBuilder.toUriString();
        log.info("[Build-server] projectId={} 调用重启开发服务器接口, url={}", projectId, url);

        RestTemplate restTemplate = new RestTemplate();
        try {
            ResponseEntity<Map<String, Object>> entity = restTemplate.exchange(url, HttpMethod.GET, null, new ParameterizedTypeReference<Map<String, Object>>() {
            });
            Map<String, Object> body = entity.getBody();
            log.info("[Build-server] projectId={} 调用重启开发服务器接口, 响应结果={}", projectId, body);
            return body;
        } catch (HttpClientErrorException e) {
            log.warn("[Build-server] projectId={} 调用重启开发服务器接口失败, status={}, responseBody={}", projectId, e.getStatusCode(), e.getResponseBodyAsString());
            return parseClientErr(projectId, e);
        }
    }

    public Map<String, Object> createProject(Long projectId) {
        String url = baseUrl + "/project/create-project";
        log.info("[Build-server] projectId={} 调用create-project接口, url={}", projectId, url);

        // 创建请求体
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("projectId", String.valueOf(projectId));

        // 设置请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        RestTemplate restTemplate = new RestTemplate();
        try {
            ResponseEntity<Map<String, Object>> entity = restTemplate.exchange(url, HttpMethod.POST, requestEntity, new ParameterizedTypeReference<Map<String, Object>>() {
            });
            Map<String, Object> body = entity.getBody();
            log.info("[Build-server] projectId={} 调用create-project接口, 响应结果={}", projectId, body);
            return body;
        } catch (HttpClientErrorException e) {
            log.warn("[Build-server] projectId={} 调用create-project接口失败, status={}, responseBody={}", projectId, e.getStatusCode(), e.getResponseBodyAsString());
            return parseClientErr(projectId, e);
        }
    }

    public Map<String, Object> uploadProject(Long projectId, MultipartFile file, Integer codeVersion, Integer pid, String devProxyPath) {
        String url = baseUrl + "/project/upload-project";
        log.info("[Build-server] projectId={} 调用上传项目接口, url={}, codeVersion={}", projectId, url, codeVersion);

        // 创建请求体，包含文件、projectId和版本号
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        // 创建MultiValueMap来存储文件、projectId和版本号
        LinkedMultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", file.getResource());
        body.add("projectId", String.valueOf(projectId));
        body.add("codeVersion", String.valueOf(codeVersion));
        body.add("pid", pid);
        if (devProxyPath != null) {
            body.add("basePath", devProxyPath);
        }

        HttpEntity<LinkedMultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        RestTemplate restTemplate = new RestTemplate();
        try {
            ResponseEntity<Map<String, Object>> entity = restTemplate.exchange(url, HttpMethod.POST, requestEntity, new ParameterizedTypeReference<Map<String, Object>>() {
            });
            Map<String, Object> responseBody = entity.getBody();
            log.info("[Build-server] projectId={} 调用上传项目接口, 响应结果={}", projectId, responseBody);
            return responseBody;
        } catch (HttpClientErrorException e) {
            log.warn("[Build-server] projectId={} 调用上传项目接口失败, status={}, responseBody={}", projectId, e.getStatusCode(), e.getResponseBodyAsString());
            return parseClientErr(projectId, e);
        }
    }

    public Map<String, Object> getProjectContent(Long projectId, String command, String proxyPath) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/project/get-project-content")
                .queryParam("projectId", projectId)
                .queryParam("command", command)
                .queryParam("proxyPath", proxyPath)
                .toUriString();
        log.info("[Build-server] projectId={} 调用查询项目内容接口, url={}", projectId, url);

        RestTemplate restTemplate = new RestTemplate();
        try {
            ResponseEntity<Map<String, Object>> entity = restTemplate.exchange(url, HttpMethod.GET, null, new ParameterizedTypeReference<Map<String, Object>>() {
            });
            Map<String, Object> body = entity.getBody();
            log.info("[Build-server] projectId={} 调用查询项目内容接口, 已响应", projectId);
            return body;
        } catch (HttpClientErrorException e) {
            log.warn("[Build-server] projectId={} 调用查询项目内容接口失败, status={}, responseBody={}", projectId, e.getStatusCode(), e.getResponseBodyAsString());
            return parseClientErr(projectId, e);
        }
    }

    public Map<String, Object> getProjectContentByVersion(Long projectId, Integer codeVersion, String command, String proxyPath) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/project/get-project-content-by-version")
                .queryParam("projectId", projectId)
                .queryParam("codeVersion", codeVersion)
                .queryParam("command", command)
                .queryParam("proxyPath", proxyPath)
                .toUriString();
        log.info("[Build-server] projectId={} 调用查询项目历史版本内容接口, url={}", projectId, url);

        RestTemplate restTemplate = new RestTemplate();
        try {
            ResponseEntity<Map<String, Object>> entity = restTemplate.exchange(url, HttpMethod.GET, null, new ParameterizedTypeReference<Map<String, Object>>() {
            });
            Map<String, Object> body = entity.getBody();
            log.info("[Build-server] projectId={} 调用查询项目历史版本内容接口, 已响应", projectId);
            return body;
        } catch (HttpClientErrorException e) {
            log.warn("[Build-server] projectId={} 调用查询项目历史版本内容接口失败, status={}, responseBody={}", projectId, e.getStatusCode(), e.getResponseBodyAsString());
            return parseClientErr(projectId, e);
        }
    }

    /**
     * codeVersion 是当前版本，上传后版本会+1
     */
    public Map<String, Object> specifiedFilesUpdate(Long projectId, List<PageFileInfo> files, Integer codeVersion, String devProxyPath, Integer devPid) {
        String url = baseUrl + "/project/specified-files-update";
        log.info("[Build-server] projectId={} 指定文件修改, url={}", projectId, url);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("projectId", String.valueOf(projectId));
        requestBody.put("files", files);
        requestBody.put("codeVersion", codeVersion);
        requestBody.put("basePath", devProxyPath);
        requestBody.put("pid", devPid);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        RestTemplate restTemplate = new RestTemplate();
        try {
            ResponseEntity<Map<String, Object>> entity = restTemplate.exchange(url, HttpMethod.POST, requestEntity, new ParameterizedTypeReference<Map<String, Object>>() {
            });
            Map<String, Object> body = entity.getBody();
            log.info("[Build-server] projectId={} 指定文件修改, 响应结果={}", projectId, body);
            return body;
        } catch (HttpClientErrorException e) {
            log.warn("[Build-server] projectId={} 调用指定文件修改接口失败, status={}, responseBody={}", projectId, e.getStatusCode(), e.getResponseBodyAsString());
            return parseClientErr(projectId, e);
        }
    }

    /**
     * codeVersion 是当前版本，上传后版本会+1
     */
    public Map<String, Object> allFilesUpdate(Long projectId, List<PageFileInfo> files, Integer codeVersion, String devProxyPath, Integer devPid) {
        String url = baseUrl + "/project/all-files-update";
        log.info("[Build-server] projectId={} 全量文件修改, url={}", projectId, url);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("projectId", String.valueOf(projectId));
        requestBody.put("files", files);
        requestBody.put("codeVersion", codeVersion);
        requestBody.put("basePath", devProxyPath);
        requestBody.put("pid", devPid);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        RestTemplate restTemplate = new RestTemplate();
        try {
            ResponseEntity<Map<String, Object>> entity = restTemplate.exchange(url, HttpMethod.POST, requestEntity, new ParameterizedTypeReference<Map<String, Object>>() {
            });
            Map<String, Object> body = entity.getBody();
            log.info("[Build-server] projectId={} 全量文件修改, 响应结果={}", projectId, body);
            return body;
        } catch (HttpClientErrorException e) {
            log.warn("[Build-server] projectId={} 调用全量文件修改接口失败, status={}, responseBody={}", projectId, e.getStatusCode(), e.getResponseBodyAsString());
            return parseClientErr(projectId, e);
        }
    }

    /**
     * codeVersion 是当前版本，上传后版本会+1
     */
    public Map<String, Object> uploadSingleFile(Long projectId, MultipartFile file, String filePath, Integer codeVersion) {
        String url = baseUrl + "/project/upload-single-file";
        log.info("[Build-server] projectId={} 上传单个文件, url={}, filePath={}, codeVersion={}", projectId, url, filePath, codeVersion);

        // 创建请求体，包含文件、projectId、filePath和codeVersion
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        // 创建MultiValueMap来存储文件和其他参数
        LinkedMultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", file.getResource());
        body.add("projectId", String.valueOf(projectId));
        body.add("filePath", filePath);
        body.add("codeVersion", String.valueOf(codeVersion));

        HttpEntity<LinkedMultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        RestTemplate restTemplate = new RestTemplate();
        try {
            ResponseEntity<Map<String, Object>> entity = restTemplate.exchange(url, HttpMethod.POST, requestEntity, new ParameterizedTypeReference<Map<String, Object>>() {
            });
            Map<String, Object> responseBody = entity.getBody();
            log.info("[Build-server] projectId={} 上传单个文件, 响应结果={}", projectId, responseBody);
            return responseBody;
        } catch (HttpClientErrorException e) {
            log.warn("[Build-server] projectId={} 调用上传单个文件接口失败, status={}, responseBody={}", projectId, e.getStatusCode(), e.getResponseBodyAsString());
            return parseClientErr(projectId, e);
        }
    }

    public Map<String, Object> uploadAttachmentFile(Long projectId, MultipartFile file, String uploadFileName) {
        String url = baseUrl + "/project/upload-attachment-file";
        log.info("[Build-server] projectId={} 上传附件, url={}, uploadFileName={}", projectId, url, uploadFileName);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        LinkedMultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", file.getResource());
        body.add("projectId", String.valueOf(projectId));
        body.add("fileName", uploadFileName);

        HttpEntity<LinkedMultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        RestTemplate restTemplate = new RestTemplate();
        try {
            ResponseEntity<Map<String, Object>> entity = restTemplate.exchange(url, HttpMethod.POST, requestEntity, new ParameterizedTypeReference<Map<String, Object>>() {
            });
            Map<String, Object> responseBody = entity.getBody();
            log.info("[Build-server] projectId={} 上传附件, 响应结果={}", projectId, responseBody);
            return responseBody;
        } catch (HttpClientErrorException e) {
            log.warn("[Build-server] projectId={} 调用上传附件接口失败, status={}, responseBody={}", projectId, e.getStatusCode(), e.getResponseBodyAsString());
            return parseClientErr(projectId, e);
        }
    }

    public Map<String, Object> backupCurrentVersion(Long projectId, Integer codeVersion) {
        String url = baseUrl + "/project/backup-current-version";
        log.info("[Build-server] projectId={} 备份当前版本, url={}, codeVersion={}", projectId, url, codeVersion);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("projectId", String.valueOf(projectId));
        requestBody.put("codeVersion", codeVersion);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        RestTemplate restTemplate = new RestTemplate();
        try {
            ResponseEntity<Map<String, Object>> entity = restTemplate.exchange(url, HttpMethod.POST, requestEntity, new ParameterizedTypeReference<Map<String, Object>>() {
            });
            Map<String, Object> body = entity.getBody();
            log.info("[Build-server] projectId={} 备份当前版本, 响应结果={}", projectId, body);
            return body;
        } catch (HttpClientErrorException e) {
            log.warn("[Build-server] projectId={} 调用备份当前版本接口失败, status={}, responseBody={}", projectId, e.getStatusCode(), e.getResponseBodyAsString());
            return parseClientErr(projectId, e);
        }
    }

    /**
     * codeVersion 是当前版本，回滚后版本会+1
     */
    public Map<String, Object> rollbackVersion(Long projectId, Integer rollbackTo, Integer codeVersion, String devProxyPath, Integer devPid) {
        String url = baseUrl + "/project/rollback-version";
        log.info("[Build-server] projectId={} 回滚版本, url={}, rollbackTo={}, codeVersion={}", projectId, url, rollbackTo, codeVersion);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("projectId", String.valueOf(projectId));
        requestBody.put("rollbackTo", rollbackTo);
        requestBody.put("codeVersion", codeVersion);
        requestBody.put("basePath", devProxyPath);
        requestBody.put("pid", devPid);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        RestTemplate restTemplate = new RestTemplate();
        try {
            ResponseEntity<Map<String, Object>> entity = restTemplate.exchange(url, HttpMethod.POST, requestEntity, new ParameterizedTypeReference<Map<String, Object>>() {
            });
            Map<String, Object> body = entity.getBody();
            log.info("[Build-server] projectId={} 回滚版本, 响应结果={}", projectId, body);
            return body;
        } catch (HttpClientErrorException e) {
            log.warn("[Build-server] projectId={} 调用回滚版本接口失败, status={}, responseBody={}", projectId, e.getStatusCode(), e.getResponseBodyAsString());
            return parseClientErr(projectId, e);
        }
    }

    public InputStream exportProject(Long projectId, Integer codeVersion, String exportType, ProjectConfigExportDto configExportDto) {
        String url = baseUrl + "/project/export-project";
        log.info("[Build-server] projectId={} 导出项目, url={}, codeVersion={}, exportType={}", projectId, url, codeVersion, exportType);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("projectId", String.valueOf(projectId));
        requestBody.put("codeVersion", codeVersion);
        requestBody.put("exportType", exportType);
        requestBody.put("config", configExportDto);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<byte[]> entity = restTemplate.exchange(url, HttpMethod.POST, requestEntity, byte[].class);

        byte[] body = entity.getBody();
        log.info("[Build-server] projectId={} 导出项目, 响应大小={} bytes", projectId, body != null ? body.length : 0);
        return body != null ? new ByteArrayInputStream(body) : null;
    }

    public Map<String, Object> deleteProject(Long projectId, Integer pid) {
        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/project/delete-project").queryParam("projectId", projectId).queryParam("pid", pid);

        String url = uriComponentsBuilder.toUriString();
        log.info("[Build-server] projectId={} 调用删除项目接口, url={}", projectId, url);

        RestTemplate restTemplate = new RestTemplate();
        try {
            ResponseEntity<Map<String, Object>> entity = restTemplate.exchange(url, HttpMethod.GET, null, new ParameterizedTypeReference<Map<String, Object>>() {
            });
            Map<String, Object> body = entity.getBody();
            log.info("[Build-server] projectId={} 调用删除项目接口, 响应结果={}", projectId, body);
            return body;
        } catch (HttpClientErrorException e) {
            log.warn("[Build-server] projectId={} 调用删除项目接口失败, status={}, responseBody={}", projectId, e.getStatusCode(), e.getResponseBodyAsString());
            return parseClientErr(projectId, e);
        }
    }

    public Map<String, Object> getDevLog(Long projectId, Integer startIndex, String logType) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/build/get-dev-log")
                .queryParam("projectId", projectId)
                .queryParam("startIndex", startIndex)
                .queryParam("logType", logType)
                .toUriString();
        log.debug("[Build-server] projectId={} 调用查询日志接口, url={}", projectId, url);

        RestTemplate restTemplate = new RestTemplate();
        try {
            ResponseEntity<Map<String, Object>> entity = restTemplate.exchange(url, HttpMethod.GET, null, new ParameterizedTypeReference<Map<String, Object>>() {
            });
            Map<String, Object> body = entity.getBody();
            log.debug("[Build-server] projectId={} 调用查询日志接口, 已响应", projectId);
            return body;
        } catch (HttpClientErrorException e) {
            log.warn("[Build-server] projectId={} 调用查询日志接口失败, status={}, responseBody={}", projectId, e.getStatusCode(), e.getResponseBodyAsString());
            return parseClientErr(projectId, e);
        }
    }

    public Map<String, Object> copyProject(Long sourceProjectId, Long targetProjectId) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/project/copy-project")
                //.queryParam("sourceProjectId", sourceProjectId)
                //.queryParam("targetProjectId", targetProjectId)
                .toUriString();
        log.info("[Build-server] sourceProjectId={},targetProjectId={}, 调用复制项目接口, url={}", sourceProjectId, targetProjectId, url);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("sourceProjectId", String.valueOf(sourceProjectId));
        requestBody.put("targetProjectId", targetProjectId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        RestTemplate restTemplate = new RestTemplate();
        try {
            ResponseEntity<Map<String, Object>> entity = restTemplate.exchange(url, HttpMethod.POST, requestEntity, new ParameterizedTypeReference<Map<String, Object>>() {
            });
            Map<String, Object> body = entity.getBody();
            log.info("[Build-server] sourceProjectId={},targetProjectId={}, 调用复制项目接口, 响应结果={}", sourceProjectId, targetProjectId, body);
            return body;
        } catch (HttpClientErrorException e) {
            log.warn("[Build-server] projectId={} 调用查询日志接口失败, status={}, responseBody={}", sourceProjectId, e.getStatusCode(), e.getResponseBodyAsString());
            return parseClientErr(sourceProjectId, e);
        }
    }

    public Map<String, Object> getLogCacheStats() {
        String url = baseUrl + "/build/get-log-cache-stats";
        log.info("[Build-server] 调用获取日志缓存统计接口, url={}", url);

        RestTemplate restTemplate = new RestTemplate();
        try {
            ResponseEntity<Map<String, Object>> entity = restTemplate.exchange(url, HttpMethod.GET, null, new ParameterizedTypeReference<Map<String, Object>>() {
            });
            Map<String, Object> body = entity.getBody();
            log.info("[Build-server] 调用获取日志缓存统计接口, 响应结果={}", body);
            return body;
        } catch (HttpClientErrorException e) {
            log.warn("[Build-server] 调用获取日志缓存统计接口失败, status={}, responseBody={}", e.getStatusCode(), e.getResponseBodyAsString());
            return parseClientErr(null, e);
        }
    }

    /**
     * 获取静态文件（流式返回）
     */
    public Flux<DataBuffer> getStaticFile(String targetPrefix, String relativePath, String logId) {
        // 使用 UriComponentsBuilder 来正确处理路径编码
        String[] prefixSegments = Arrays.stream(targetPrefix.split("/")).filter(segment -> !segment.isEmpty()).toArray(String[]::new);
        String[] relativeSegments = Arrays.stream(relativePath.split("/")).filter(segment -> !segment.isEmpty()).toArray(String[]::new);

        String url = UriComponentsBuilder.fromHttpUrl(baseUrl).pathSegment(prefixSegments).pathSegment(relativeSegments).toUriString();
        log.info("[Build-server] logId={} 调用获取静态文件接口, url={}, targetPrefix={}, relativePath={}", logId, url, targetPrefix, relativePath);

        return webClient.get()
                .uri(url)
                .accept(MediaType.ALL) // 接受所有媒体类型，因为静态文件可能是图片、文本等
                .retrieve()
                .bodyToFlux(DataBuffer.class)
                .doOnError(WebClientResponseException.class, e -> {
                    log.warn("[Build-server] logId={} 调用获取静态文件接口失败, status={}, responseBody={}", logId, e.getStatusCode(), e.getResponseBodyAsString());
                }).doOnError(Throwable.class, e -> {
                    log.error("[Build-server] logId={} 调用获取静态文件接口异常", logId, e);
                }).doOnDiscard(DataBuffer.class, DataBufferUtils::release).doOnComplete(() -> {
                    log.info("[Build-server] logId={} 调用获取静态文件接口, 流式传输完成", logId);
                });
    }

    public Map<String, Object> clearAllLogCache() {
        String url = baseUrl + "/build/clear-all-log-cache";
        log.info("[Build-server] 调用清理日志缓存接口, url={}", url);

        RestTemplate restTemplate = new RestTemplate();
        try {
            ResponseEntity<Map<String, Object>> entity = restTemplate.exchange(url, HttpMethod.GET, null, new ParameterizedTypeReference<Map<String, Object>>() {
            });
            Map<String, Object> body = entity.getBody();
            log.info("[Build-server] 调用清理日志缓存接口, 响应结果={}", body);
            return body;
        } catch (HttpClientErrorException e) {
            log.warn("[Build-server] 调用清理日志缓存接口失败, status={}, responseBody={}", e.getStatusCode(), e.getResponseBodyAsString());
            return parseClientErr(null, e);
        }
    }

    // 捕获4xx错误，尝试解析响应体
    private Map<String, Object> parseClientErr(Long projectId, HttpClientErrorException e) {
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("success", false);
        resultMap.put("message", e.getMessage());
        try {
            String responseBody = e.getResponseBodyAsString();
            if (responseBody != null && !responseBody.isEmpty()) {
                ObjectMapper objectMapper = new ObjectMapper();
                @SuppressWarnings("unchecked") Map<String, Object> errorResponse = objectMapper.readValue(responseBody, Map.class);
                if (errorResponse.containsKey("code")) {
                    resultMap.put("code", errorResponse.get("code"));
                }
                if (errorResponse.containsKey("message")) {
                    resultMap.put("message", errorResponse.get("message"));
                } else if (errorResponse.containsKey("error")) {
                    Object errorObj = errorResponse.get("error");
                    if (errorObj instanceof Map) {
                        @SuppressWarnings("unchecked") Map<String, Object> errorMap = (Map<String, Object>) errorObj;
                        if (errorMap.containsKey("message")) {
                            resultMap.put("message", errorMap.get("message"));
                        }
                    }
                }
            }
        } catch (Exception parseException) {
            log.error("[Build-server] projectId={} 解析错误响应体失败", projectId, parseException);
        }
        return resultMap;
    }

}