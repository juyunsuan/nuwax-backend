package com.xspaceagi.custompage.domain.keepalive;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.xspaceagi.system.spec.enums.ErrorCodeEnum;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xspaceagi.custompage.domain.gateway.PageFileBuildClient;
import com.xspaceagi.custompage.domain.model.CustomPageBuildModel;
import com.xspaceagi.custompage.domain.proxypath.ICustomPageProxyPathService;
import com.xspaceagi.custompage.domain.repository.ICustomPageBuildRepository;
import com.xspaceagi.custompage.domain.service.ICustomPageConfigDomainService;
import com.xspaceagi.system.spec.common.UserContext;
import com.xspaceagi.system.spec.dto.ReqResult;
import com.xspaceagi.system.spec.enums.YesOrNoEnum;
import com.xspaceagi.system.spec.exception.BizException;
import com.xspaceagi.system.spec.utils.RedisUtil;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

/**
 * 保活服务
 */
@Slf4j
@Service
public class KeepAliveServiceImpl implements IKeepAliveService {

    @Resource
    private RedisUtil redisUtil;
    @Resource
    private PageFileBuildClient pageFileBuildClient;
    @Resource
    private ICustomPageBuildRepository customPageBuildRepository;
    @Resource
    private ICustomPageConfigDomainService customPageConfigDomainService;
    @Resource
    private ICustomPageProxyPathService customPageProxyPathService;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    // Redis key前缀
    private static final String KEEPALIVE_KEY_PREFIX = "dev:keepalive:";

    // 所有保活项目ID集合的key
    private static final String KEEPALIVE_PROJECTS_SET_KEY = "dev:keepalive:projects";

    /**
     * 处理保活请求
     */
    public ReqResult<Map<String, Object>> handleKeepAlive(Long projectId, UserContext userContext) {
        log.info("[KeepAlive] projectId={},开始处理保活请求", projectId);

        try {
            CustomPageBuildModel project = customPageBuildRepository.getByProjectId(projectId);
            if (project == null) {
                log.info("[KeepAlive] projectId={},项目不存在", projectId);
                return ReqResult.error("0001", "项目不存在");
            }

            String devProxyPath = customPageProxyPathService.getDevProxyPath(projectId);
            Map<String, Object> serverResp = null;

            // 需要先判断表中的状态,如果直接请求server启动dev,有可能server重启过,丢失了缓存,但dev还活着,再次请求就会多开dev服务器
            if (project.getDevPid() != null && project.getDevPort() != null) {
                // 调server保活
                log.info("[KeepAlive] projectId={},从表中取到dev已运行,调server保活接口", projectId);
                serverResp = pageFileBuildClient.keepAlive(projectId, devProxyPath, project.getDevPid(),
                        project.getDevPort());
            } else {
                // 调server启动dev
                log.info("[KeepAlive] projectId={},dev未运行,调server启动dev", projectId);
                serverResp = pageFileBuildClient.startDev(projectId, devProxyPath);

            }
            if (serverResp == null) {
                log.info("[KeepAlive] projectId={},保活失败,server返回null", projectId);
                updateKeepAlive(projectId, new Date(), YesOrNoEnum.N.getKey(), null, null, userContext);
                return ReqResult.error("9999", "保活失败，server无响应");
            }

            boolean success = Boolean.parseBoolean(String.valueOf(serverResp.get("success")));
            String message = serverResp.get("message") == null ? "" : String.valueOf(serverResp.get("message"));
            if (!success) {
                log.error("[KeepAlive] projectId={},保活失败,server返回错误,code={},message={}", projectId,
                        serverResp.get("code"), message);
                updateKeepAlive(projectId, new Date(), YesOrNoEnum.N.getKey(), null, null, userContext);

                String code = serverResp.get("code") == null ? "" : String.valueOf(serverResp.get("code"));
                if ("PROJECT_STARTING".equals(code)) {
                    return ReqResult.error(ErrorCodeEnum.PROJECT_STARTING.getCode(), ErrorCodeEnum.PROJECT_STARTING.getMsg());
                }
                return ReqResult.error("9999", message);
            }

            // 持久化 dev 运行信息
            Integer pid = null;
            Integer port = null;
            try {
                Object pidObj = serverResp.get("pid");
                Object portObj = serverResp.get("port");
                pid = Integer.valueOf(String.valueOf(pidObj));
                port = Integer.valueOf(String.valueOf(portObj));
            } catch (Exception e) {
                log.error("[KeepAlive] projectId={},获取dev端口和pid异常", projectId, e);
                updateKeepAlive(projectId, new Date(), YesOrNoEnum.N.getKey(), null, null, userContext);
                return ReqResult.error("9999", "获取服务端口和pid异常: " + e.getMessage());
            }
            updateKeepAlive(projectId, new Date(), YesOrNoEnum.Y.getKey(), pid, port, userContext);

            // 返回结果
            Map<String, Object> result = new HashMap<>();
            result.put("devRunning", YesOrNoEnum.Y.getKey());
            result.put("port", port);
            return ReqResult.success(result);
        } catch (Exception e) {
            log.error("[KeepAlive] projectId={},保活处理异常", projectId, e);
            return ReqResult.error("9999", "保活处理异常: " + e.getMessage());
        }
    }

    /**
     * 更新保活信息
     */
    public void updateKeepAlive(Long projectId,
            Date keepAliveTime,
            Integer devRunning,
            Integer devPid,
            Integer devPort,
            UserContext userContext) {
        CustomPageBuildModel keepAliveModel = new CustomPageBuildModel();
        keepAliveModel.setProjectId(projectId);
        keepAliveModel.setLastKeepAliveTime(keepAliveTime);
        keepAliveModel.setDevRunning(devRunning);
        keepAliveModel.setDevPid(devPid);
        keepAliveModel.setDevPort(devPort);
        keepAliveModel.setTenantId(userContext.getTenantId());

        // 更新Redis缓存
        updateKeepAliveCache(projectId, keepAliveModel);

        // 更新数据库
        updatKeepAliveDb(keepAliveModel, userContext);
    }

    /**
     * 更新缓存
     */
    private void updateKeepAliveCache(Long projectId, CustomPageBuildModel model) {
        try {
            String key = KEEPALIVE_KEY_PREFIX + projectId;
            String value = objectMapper.writeValueAsString(model);

            // 不设置过期时间，手动管理生命周期
            redisUtil.set(key, value);

            // 将projectId添加到保活项目集合中
            redisUtil.sSet(KEEPALIVE_PROJECTS_SET_KEY, projectId.toString());

            log.info("[KeepAlive] projectId={},缓存已更新", projectId);
        } catch (Exception e) {
            log.error("[KeepAlive] projectId={},缓存更新失败", projectId, e);
        }
    }

    /**
     * 更新库表
     */
    private void updatKeepAliveDb(CustomPageBuildModel model, UserContext userContext) {
        try {
            customPageBuildRepository.updateKeepAlive(model, userContext);
            log.info("[KeepAlive] projectId={},库表已更新", model.getProjectId());
        } catch (Exception e) {
            log.error("[KeepAlive] projectId={},库表更新失败", model.getProjectId(), e);
            throw new BizException("KeepAlive更新库表失败");
        }
    }

    /**
     * 删除保活缓存
     */
    @Override
    public void removeKeepAliveCache(Long projectId) {
        redisUtil.expire(KEEPALIVE_KEY_PREFIX + projectId, 0);

        redisUtil.remove(KEEPALIVE_PROJECTS_SET_KEY, projectId.toString());

        log.info("[KeepAlive] projectId={},删除redis保活缓存完成", projectId);
    }
}