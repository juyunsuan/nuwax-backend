package com.xspaceagi.custompage.domain.service.impl;

import com.xspaceagi.custompage.domain.gateway.PageFileBuildClient;
import com.xspaceagi.custompage.domain.service.ICustomPageFileDomainService;
import com.xspaceagi.system.spec.common.UserContext;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;

/**
 * 通用文件领域服务实现
 */
@Slf4j
@Service
public class CustomPageFileDomainServiceImpl implements ICustomPageFileDomainService {

    @Resource
    private PageFileBuildClient pageFileBuildClient;

    @Override
    public Flux<DataBuffer> getStaticFile(String targetPrefix, String relativePath, String logId, UserContext userContext) {
        log.info("[Domain] logId={}, 获取静态文件,targetPrefix={}, relativePath={}", logId, targetPrefix, relativePath);
        return pageFileBuildClient.getStaticFile(targetPrefix, relativePath, logId)
                .doOnError(WebClientResponseException.class, e -> {
                    log.error("[Domain] logId={}, 获取静态文件失败, targetPrefix={}, relativePath={}, status={}",
                            logId, targetPrefix, relativePath, e.getStatusCode());
                })
                .doOnError(Throwable.class, e -> {
                    log.error("[Domain] logId={}, 获取静态文件异常, targetPrefix={}, relativePath={}", logId, targetPrefix, relativePath, e);
                });
    }

}

