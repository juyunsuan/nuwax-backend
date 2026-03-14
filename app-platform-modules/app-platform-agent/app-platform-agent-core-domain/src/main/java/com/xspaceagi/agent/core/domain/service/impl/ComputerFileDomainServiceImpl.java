package com.xspaceagi.agent.core.domain.service.impl;

import com.xspaceagi.agent.core.domain.service.IComputerFileDomainService;
import com.xspaceagi.agent.core.infra.rpc.ComputerFileClient;
import com.xspaceagi.agent.core.adapter.dto.ComputerFileInfo;
import com.xspaceagi.system.spec.common.UserContext;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * 文件领域服务实现
 */
@Slf4j
@Service
public class ComputerFileDomainServiceImpl implements IComputerFileDomainService {

    @Resource
    private ComputerFileClient computerFileClient;

    @Override
    public Flux<DataBuffer> getStaticFile(Long cId, String targetPrefix, String relativePath, String logId) {
        log.info("[Domain] logId={}, 获取静态文件, targetPrefix={}, relativePath={}", logId, targetPrefix, relativePath);
        return computerFileClient.getStaticFile(cId, targetPrefix, relativePath, logId)
                .doOnError(WebClientResponseException.class, e -> {
                    log.error("[Domain] logId={}, 获取静态文件失败, targetPrefix={}, relativePath={}, status={}",
                            logId, targetPrefix, relativePath, e.getStatusCode());
                })
                .doOnError(Throwable.class, e -> {
                    log.error("[Domain] logId={}, 获取静态文件异常, targetPrefix={}, relativePath={}", logId, targetPrefix, relativePath, e);
                });
    }

    @Override
    public Map<String, Object> getFileList(Long userId, Long cId, String proxyPath, UserContext userContext) {
        log.info("[Domain] 查询文件列表, userId={}, cId={}", userId, cId);
        return computerFileClient.getFileList(userId, cId, proxyPath);
    }

    @Override
    public Map<String, Object> filesUpdate(Long userId, Long cId, List<ComputerFileInfo> files, UserContext userContext) {
        log.info("[Domain] 更新文件列表, userId={}, cId={}", userId, cId);
        return computerFileClient.filesUpdate(userId, cId, files);
    }

    @Override
    public Map<String, Object> uploadFile(Long userId, Long cId, String filePath, MultipartFile file, UserContext userContext) {
        log.info("[Domain] 上传文件, userId={}, cId={}, filePath={}", userId, cId, filePath);
        return computerFileClient.uploadFile(userId, cId, filePath, file);
    }

    @Override
    public Map<String, Object> uploadFiles(Long userId, Long cId, List<String> filePaths, List<MultipartFile> files, UserContext userContext) {
        log.info("[Domain] 批量上传文件, userId={}, cId={}, fileCount={}", userId, cId, files != null ? files.size() : 0);
        return computerFileClient.uploadFiles(userId, cId, filePaths, files);
    }

    @Override
    public Flux<DataBuffer> downloadAllFiles(Long userId, Long cId, String logId, UserContext userContext) {
        log.info("[Domain] 下载全部文件, logId={}, userId={}, cId={}", logId, userId, cId);
        return computerFileClient.downloadAllFiles(userId, cId, logId)
                .doOnError(WebClientResponseException.class, e -> {
                    log.error("[Domain] 下载全部文件失败, logId={}, status={}", logId, e.getStatusCode());
                })
                .doOnError(Throwable.class, e -> {
                    log.error("[Domain] 下载全部文件异常, logId={}", logId, e);
                });
    }
}

