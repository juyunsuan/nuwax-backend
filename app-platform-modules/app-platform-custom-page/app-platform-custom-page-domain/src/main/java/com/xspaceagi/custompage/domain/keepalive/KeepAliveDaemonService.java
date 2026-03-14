package com.xspaceagi.custompage.domain.keepalive;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xspaceagi.custompage.domain.gateway.PageFileBuildClient;
import com.xspaceagi.custompage.domain.model.CustomPageBuildModel;
import com.xspaceagi.custompage.domain.repository.ICustomPageBuildRepository;
import com.xspaceagi.system.spec.common.RequestContext;
import com.xspaceagi.system.spec.tenant.thread.TenantFunctions;
import com.xspaceagi.system.spec.utils.RedisUtil;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

/**
 * 保活守护程序
 */
@Slf4j
@Service
public class KeepAliveDaemonService {

    @Resource
    private RedisUtil redisUtil;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private PageFileBuildClient pageFileBuildClient;
    @Resource
    private ICustomPageBuildRepository customPageBuildRepository;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    // 保活超时时间：5分钟
    private static final long KEEPALIVE_TIMEOUT_MS = 5 * 60 * 1000;

    // 定时检查间隔：20秒
    private static final long CHECK_INTERVAL_SECONDS = 20;

    // Redis key前缀
    private static final String KEEPALIVE_KEY_PREFIX = "dev:keepalive:";

    // 分布式锁key
    private static final String KEEPALIVE_LOCK_KEY = "dev:keepalive:lock:check";

    // 所有保活项目ID集合的key
    private static final String KEEPALIVE_PROJECTS_SET_KEY = "dev:keepalive:projects";

    private ScheduledExecutorService scheduledExecutor;

    @PostConstruct
    public void init() {
        // 启动定时检查任务
        scheduledExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "dev-keepalive-checker");
            thread.setDaemon(true);
            return thread;
        });

        // 启动时从数据库加载 devRunning=1 的项目到缓存
        try {
            TenantFunctions.callWithIgnoreCheck(this::warmupKeepAliveCache);
        } catch (Exception e) {
            log.error("[KeepAlive-daemon] 预热Redis保活缓存失败", e);
        }

        scheduledExecutor.scheduleWithFixedDelay(
                this::checkKeepAliveTimeout,
                CHECK_INTERVAL_SECONDS,
                CHECK_INTERVAL_SECONDS,
                TimeUnit.SECONDS);

        log.info("[KeepAlive-daemon] 保活服务初始化完成，定时检查间隔：{}秒", CHECK_INTERVAL_SECONDS);
    }

    @PreDestroy
    public void destroy() {
        if (scheduledExecutor != null && !scheduledExecutor.isShutdown()) {
            scheduledExecutor.shutdown();
            try {
                if (!scheduledExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduledExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduledExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        log.info("[KeepAlive-daemon] 保活服务已关闭");
    }

    /**
     * 启动时预热Redis保活缓存：从数据库导入 devRunning=1 的项目
     */
    private Integer warmupKeepAliveCache() {
        // 查询运行中的项目
        List<CustomPageBuildModel> runningList = customPageBuildRepository.listByDevRunning(1);
        if (runningList == null || runningList.isEmpty()) {
            log.info("[KeepAlive-daemon] 预热结束：无运行中的项目");
            return 1;
        }

        log.info("[KeepAlive-daemon] 开始预热，总计：{}", runningList.size());
        int success = 0;
        for (CustomPageBuildModel model : runningList) {
            try {
                if (model.getProjectId() == null) {
                    continue;
                }
                updateKeepAliveCache(model.getProjectId(), model);
                success++;
            } catch (Exception e) {
                log.error("[KeepAlive-daemon] projectId={},预热失败", model.getProjectId(), e);
            }
        }
        log.info("[KeepAlive-daemon] 预热完成,总计：{}, 成功：{}", runningList.size(), success);
        return success;
    }

    /**
     * 定时检查保活超时
     */
    private void checkKeepAliveTimeout() {
        TenantFunctions.callWithIgnoreCheck(() -> {
            log.debug("[KeepAlive-daemon] 定时检查超时项目");
            checkKeepAliveTimeout0();
            return null;
        });
    }

    /**
     * 定时检查保活超时
     */
    private void checkKeepAliveTimeout0() {
        // 使用分布式锁确保只有一个实例执行检查
        RLock lock = redissonClient.getLock(KEEPALIVE_LOCK_KEY);
        try {
            // 尝试获取锁，最多等待1秒，锁持有时间最多30秒
            if (lock.tryLock(1, 30, TimeUnit.SECONDS)) {
                try {
                    Date now = new Date();
                    long timeoutThreshold = now.getTime() - KEEPALIVE_TIMEOUT_MS;

                    //log.info("[KeepAlive-daemon] 开始检查超时项目，当前时间：{}", now);

                    // 从Redis获取所有保活项目ID
                    Set<Object> projectIds = redisUtil.members(KEEPALIVE_PROJECTS_SET_KEY);
                    if (projectIds == null || projectIds.isEmpty()) {
                        //log.debug("[KeepAlive-daemon] redis中没有需要检查的项目,结束");
                        return;
                    }

                    log.info("[KeepAlive-daemon] 找到{}个项开始检查", projectIds.size());

                    // 检查每个项目的保活状态
                    for (Object projectIdObj : projectIds) {
                        Long projectId = Long.valueOf(projectIdObj.toString());
                        //log.debug("[KeepAlive-daemon] 开始检查：projectId={} --", projectId);
                        try {
                            CustomPageBuildModel model = getKeepAliveCache(projectId);
                            if (model == null || model.getLastKeepAliveTime() == null) {
                                // 如果Redis中没有数据，从保活项目集合中移除
                                //log.debug("[KeepAlive-daemon] projectId={},redis中没有数据,从保活集合中移除", projectId);
                                redisUtil.remove(KEEPALIVE_PROJECTS_SET_KEY, projectId.toString());
                                continue;
                            }

                            long lastKeepAliveTime = model.getLastKeepAliveTime().getTime();
                            if (lastKeepAliveTime < timeoutThreshold) {
                                //log.debug("[KeepAlive-daemon] projectId={},项目超时,最后保活时间：{},开始执行stop服务器", projectId, model.getLastKeepAliveTime());

                                // 停止开发服务器
                                stopDevServerIfRunning(projectId, model);
                            }
                        } catch (Exception e) {
                            log.error("[KeepAlive-daemon] projectId={},检查保活异常", projectId, e);
                        }
                    }

                    //log.debug("[KeepAlive-daemon] 保活检查完成");
                } finally {
                    lock.unlock();
                }
            } else {
                log.info("[KeepAlive-daemon] 获取分布式锁失败,跳过本次检查");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[KeepAlive-daemon] 获取分布式锁被中断", e);
        } catch (Exception e) {
            log.error("[KeepAlive-daemon] 检查保活异常", e);
        }
    }

    /**
     * 更新Redis中的保活缓存
     */
    private void updateKeepAliveCache(Long projectId, CustomPageBuildModel model) {
        try {
            String key = KEEPALIVE_KEY_PREFIX + projectId;
            String value = objectMapper.writeValueAsString(model);

            // 不设置过期时间，手动管理生命周期
            redisUtil.set(key, value);

            // 将projectId添加到保活项目集合中
            redisUtil.sSet(KEEPALIVE_PROJECTS_SET_KEY, projectId.toString());

            log.info("[KeepAlive-daemon] 更新Redis保活缓存，projectId={}", projectId);
        } catch (JsonProcessingException e) {
            log.error("[KeepAlive-daemon] 序列化保活数据失败，projectId={}", projectId, e);
        }
    }

    /**
     * 从Redis删除保活缓存
     */
    private void removeKeepAliveCache(Long projectId) {
        String key = KEEPALIVE_KEY_PREFIX + projectId;
        // 直接删除key，而不是设置过期时间
        redisUtil.expire(key, 0);

        // 从保活项目集合中移除projectId
        redisUtil.remove(KEEPALIVE_PROJECTS_SET_KEY, projectId.toString());

        log.info("[KeepAlive-daemon] projectId={},删除redis保活缓存完成", projectId);
    }

    /**
     * 停止开发服务器（如果正在运行）
     */
    private void stopDevServerIfRunning(Long projectId, CustomPageBuildModel model) {
        try {
            if (model.getDevRunning() != null && model.getDevRunning() == 1 && model.getDevPid() != null) {
                log.info("[KeepAlive-daemon] projectId={},pid={},开始stop服务器",
                        projectId, model.getDevPid());

                // 调用停止开发服务器的逻辑
                if (model.getDevPid() != null) {
                    Map<String, Object> resp = pageFileBuildClient.stopDev(projectId, model.getDevPid());

                    if (resp == null) {
                        log.error("[KeepAlive-daemon] projectId={},stop服务器失败,server无响应", projectId);
                        return;
                    }
                    boolean success = Boolean.parseBoolean(String.valueOf(resp.get("success")));
                    String message = resp.get("message") == null ? "" : String.valueOf(resp.get("message"));
                    if (!success) {
                        log.error("[KeepAlive-daemon] projectId={},stop服务器失败,server返回错误,message={}", projectId, message);
                        return;
                    }
                    log.info("[KeepAlive-daemon] projectId={},stop服务器成功,开始更新状态", projectId);

                    Long tenantId = model.getTenantId();
                    if (tenantId == null) {
                        CustomPageBuildModel dbModel = customPageBuildRepository.getByProjectId(projectId);
                        tenantId = dbModel.getTenantId();
                    }

                    RequestContext requestContext = RequestContext.get();
                    if (requestContext == null) {
                        requestContext = new RequestContext<>();
                        requestContext.setTenantId(tenantId);
                        RequestContext.set(requestContext);
                    }

                    // 从Redis缓存中移除
                    removeKeepAliveCache(projectId);
                    // 更新数据库状态
                    customPageBuildRepository.updateStopDevStatus(projectId, null);

                    log.info("[KeepAlive-daemon] projectId={},dev服务器已停止", projectId);
                }
            } else {
                // 从Redis缓存中移除
                removeKeepAliveCache(projectId);
                log.info("[KeepAlive-daemon] projectId={},dev服务器已停止", projectId);
            }
        } catch (Exception e) {
            log.error("[KeepAlive-daemon] projectId={}stop服务器异常", projectId, e);
        } finally {
            RequestContext.remove();
        }
    }

    /**
     * 从Redis获取保活缓存
     */
    private CustomPageBuildModel getKeepAliveCache(Long projectId) {
        try {
            String key = KEEPALIVE_KEY_PREFIX + projectId;
            Object value = redisUtil.get(key);
            if (value != null) {
                return objectMapper.readValue(value.toString(), CustomPageBuildModel.class);
            }
        } catch (JsonProcessingException e) {
            log.error("[KeepAlive-daemon] 反序列化保活数据失败，projectId={}", projectId, e);
        }
        return null;
    }
}
