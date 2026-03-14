package com.xspaceagi.agent.core.infra.rpc;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.xspaceagi.agent.core.adapter.application.ConversationApplicationService;
import com.xspaceagi.agent.core.adapter.dto.ConversationDto;
import com.xspaceagi.agent.core.infra.rpc.dto.SandboxServerConfig;
import com.xspaceagi.sandbox.sdk.server.ISandboxConfigRpcService;
import com.xspaceagi.sandbox.sdk.service.dto.SandboxConfigRpcDto;
import com.xspaceagi.sandbox.sdk.service.dto.SandboxGlobalConfigDto;
import com.xspaceagi.sandbox.sdk.service.dto.SandboxServerInfo;
import com.xspaceagi.sandbox.spec.enums.SandboxScopeEnum;
import com.xspaceagi.system.application.dto.TenantConfigDto;
import com.xspaceagi.system.application.service.TenantConfigApplicationService;
import com.xspaceagi.system.sdk.service.AbstractTaskExecuteService;
import com.xspaceagi.system.sdk.service.ScheduleTaskApiService;
import com.xspaceagi.system.sdk.service.dto.ScheduleTaskDto;
import com.xspaceagi.system.spec.exception.BizException;
import com.xspaceagi.system.spec.tenant.thread.TenantFunctions;
import com.xspaceagi.system.spec.utils.RedisUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Component("sandboxServerConfigService")
public class SandboxServerConfigService extends AbstractTaskExecuteService {


    static {
        // disable keep alive，暂不使用连接池
        System.setProperty("jdk.httpclient.keepalive.timeout", "0");
    }

    @Resource
    private TenantConfigApplicationService tenantConfigApplicationService;

    @Resource
    private ConversationApplicationService conversationApplicationService;

    @Resource
    private ScheduleTaskApiService scheduleTaskApiService;

    @Resource
    private RedisUtil redisUtil;

    @Resource
    private ISandboxConfigRpcService iSandboxConfigRpcService;

    private final HttpClient httpClient = java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private final Map<Long, Map<String, ServerStatus>> serverStatusMap = new ConcurrentHashMap<>();

    private final AtomicInteger index = new AtomicInteger(0);

    @PostConstruct
    public void init() {
        scheduleTaskApiService.start(ScheduleTaskDto.builder()
                .taskId("sandboxServerConfigService")
                .beanId("sandboxServerConfigService")
                .maxExecTimes(Long.MAX_VALUE)
                .cron(ScheduleTaskDto.Cron.EVERY_5_SECOND.getCron())
                .params(Map.of())
                .build());
    }


    //根据 cId 获取沙箱服务器（自动从会话获取 tenantId）
    public SandboxServerConfig.SandboxServer selectServer(Long cid) {
        return TenantFunctions.callWithIgnoreCheck(() -> {
            ConversationDto conversation = conversationApplicationService.getConversationByCid(cid);
            if (conversation == null) {
                throw new BizException("会话不存在");
            }
            TenantConfigDto tenantConfig = tenantConfigApplicationService.getTenantConfig(conversation.getTenantId());
            String sandboxServerId = conversation.getSandboxServerId();
            SandboxServerConfig.SandboxServer sandboxServer = selectServer(tenantConfig, conversation.getUserId(), sandboxServerId);
            if (conversation.getSandboxServerId() == null || !conversation.getSandboxServerId().equals(sandboxServer.getServerId())) {
                conversationApplicationService.updateConversationSandboxServerId(conversation.getId(), sandboxServer.getServerId());
            }
            conversation.setSandboxServerId(sandboxServer.getServerId());
            sandboxServer.setCurrentConversation(conversation);
            return sandboxServer;
        });
    }

    public SandboxServerConfig.SandboxServer selectServer(TenantConfigDto tenantConfig, Long userId, String serverId) {

        if (!serverStatusMap.containsKey(tenantConfig.getTenantId())) {
            serverStatusMap.put(tenantConfig.getTenantId(), new ConcurrentHashMap<>());
        }
        if (serverId != null && !serverId.equals("-1")) {// -1 代表 global sandboxes
            try {
                SandboxConfigRpcDto sandboxConfigRpcDto = iSandboxConfigRpcService.queryById(Long.parseLong(serverId));
                if (sandboxConfigRpcDto != null) {
                    if (sandboxConfigRpcDto.getScope() == SandboxScopeEnum.USER && !sandboxConfigRpcDto.getIsActive()) {
                        throw new BizException("智能体客户端已停用");
                    } else if (!sandboxConfigRpcDto.getIsActive()) {
                        throw new BizException("会话所在智能体电脑服务端已停用");
                    }
                    if (sandboxConfigRpcDto.getScope() == SandboxScopeEnum.USER && !sandboxConfigRpcDto.isOnline()) {
                        throw new BizException("智能体客户端已离线");
                    }
                    SandboxGlobalConfigDto globalConfig = iSandboxConfigRpcService.getGlobalConfig(tenantConfig.getTenantId());
                    return convertToSandboxServer(sandboxConfigRpcDto, globalConfig);
                }
            } catch (NumberFormatException e) {
                log.warn("无法将serverId转换为Long类型，跳过该记录: {}", serverId);
                // 忽略
            }
        }

        if (serverId == null || serverId.equals("-1")) { // -1 代表 global sandboxes
            Object o = redisUtil.get("user_sandbox_server_" + userId);
            if (o != null) {
                serverId = o.toString();
            }
        }
        SandboxServerConfig sandboxServerConfig = buildGloabalSandboxServerConfig(tenantConfig);
        for (SandboxServerConfig.SandboxServer sandboxServer : sandboxServerConfig.getSandboxServers()) {
            if (sandboxServer.getServerId().equals(serverId)) {
                return sandboxServer;
            }
        }

        SandboxServerConfig.SandboxServer sandboxServer = selectServer(tenantConfig, new ArrayList<>(sandboxServerConfig.getSandboxServers()));
        redisUtil.set("user_sandbox_server_" + userId, sandboxServer.getServerId(), 86400);
        return sandboxServer;
    }

    public SandboxConfigRpcDto queryById(Long id) {
        return iSandboxConfigRpcService.queryById(id);
    }

    private SandboxServerConfig.SandboxServer selectServer(TenantConfigDto tenantConfig, List<SandboxServerConfig.SandboxServer> sandboxServers) {
        if (sandboxServers.isEmpty()) {
            throw new BizException("当前过于火爆，请稍后再试");//当前智能体沙箱资源已达上限
        }
        SandboxServerConfig.SandboxServer sandboxServer = sandboxServers.get(index.getAndIncrement() % sandboxServers.size());
        Map<String, ServerStatus> statusMap = serverStatusMap.get(tenantConfig.getTenantId());
        ServerStatus serverStatus = statusMap.get(sandboxServer.getServerId());
        if (serverStatus == null) {
            return sandboxServer;
        }
        if (serverStatus.getCurrentUsers() >= sandboxServer.getMaxUsers()) {
            sandboxServers.remove(sandboxServer);
            return selectServer(tenantConfig, sandboxServers);
        }
        return sandboxServer;
    }

    private SandboxServerConfig buildGloabalSandboxServerConfig(TenantConfigDto tenantConfig) {
        SandboxServerConfig sandboxServerConfig;
        List<SandboxConfigRpcDto> sandboxConfigRpcDtos = iSandboxConfigRpcService.queryGlobalConfigs(tenantConfig.getTenantId());
        if (CollectionUtils.isEmpty(sandboxConfigRpcDtos) && tenantConfig.getTenantId() != 1L) {
            //全局没有配置时读1的配置
            sandboxConfigRpcDtos = iSandboxConfigRpcService.queryGlobalConfigs(1L);
        }
        sandboxServerConfig = new SandboxServerConfig();
        SandboxGlobalConfigDto globalConfig = iSandboxConfigRpcService.getGlobalConfig(tenantConfig.getTenantId());
        try {
            sandboxServerConfig.setPerUserMemoryGB(Double.parseDouble(globalConfig.getPerUserMemoryGB()));
            sandboxServerConfig.setPerUserCpuCores(Integer.parseInt(globalConfig.getPerUserCpuCores()));
        } catch (NumberFormatException e) {
            sandboxServerConfig.setPerUserMemoryGB(4);
            sandboxServerConfig.setPerUserCpuCores(2);
        }
        sandboxServerConfig.setSandboxServers(sandboxConfigRpcDtos.stream().map(sandboxConfigRpcDto -> convertToSandboxServer(sandboxConfigRpcDto, globalConfig)).collect(Collectors.toList()));

        Assert.notEmpty(sandboxServerConfig.getSandboxServers(), "未配置智能体电脑");
        sandboxServerConfig.getSandboxServers().forEach(sandboxServer -> {
            if (sandboxServer.getPerUserCpuCores() == 0) {
                sandboxServer.setPerUserCpuCores(sandboxServerConfig.getPerUserCpuCores());
            }
            // CPU核数适当限制
            Assert.isTrue(sandboxServer.getPerUserCpuCores() > 0 && sandboxServer.getPerUserCpuCores() < 32, "用户沙箱环境CPU配置错误");
            if (sandboxServer.getPerUserMemoryGB() == 0) {
                sandboxServer.setPerUserMemoryGB(sandboxServerConfig.getPerUserMemoryGB());
            }
            Assert.isTrue(sandboxServer.getPerUserMemoryGB() > 0 && sandboxServer.getPerUserMemoryGB() < 64, "用户沙箱环境内存配置错误");
        });
        return sandboxServerConfig;
    }

    private SandboxServerConfig.SandboxServer convertToSandboxServer(SandboxConfigRpcDto sandboxConfigRpcDto, SandboxGlobalConfigDto sandboxGlobalConfigDto) {
        SandboxServerConfig.SandboxServer sandboxServer = new SandboxServerConfig.SandboxServer();
        sandboxServer.setServerId(sandboxConfigRpcDto.getId().toString());
        sandboxServer.setServerName(sandboxConfigRpcDto.getName());
        String hostWithScheme = sandboxConfigRpcDto.getConfigValue().getHostWithScheme();
        if (StringUtils.isBlank(hostWithScheme)) {
            throw new IllegalArgumentException("智能体电脑配置错误");
        }
        sandboxServer.setScope(sandboxConfigRpcDto.getScope());
        if (sandboxConfigRpcDto.getScope() == SandboxScopeEnum.GLOBAL) {
            if (hostWithScheme.endsWith("/")) {
                hostWithScheme = hostWithScheme.substring(0, hostWithScheme.length() - 1);
            }
            sandboxServer.setServerAgentUrl(hostWithScheme + ":" + sandboxConfigRpcDto.getConfigValue().getAgentPort());
            sandboxServer.setServerVncUrl(hostWithScheme + ":" + sandboxConfigRpcDto.getConfigValue().getVncPort());
            sandboxServer.setServerFileUrl(hostWithScheme + ":" + sandboxConfigRpcDto.getConfigValue().getFileServerPort());
            sandboxServer.setServerApiKey(sandboxConfigRpcDto.getConfigValue().getApiKey());
            sandboxServer.setMaxUsers(sandboxConfigRpcDto.getConfigValue().getMaxUsers());
        } else {
            SandboxServerInfo sandboxServerInfo = sandboxConfigRpcDto.getSandboxServerInfo();
            if (sandboxServerInfo == null) {
                throw new IllegalArgumentException("智能体电脑配置错误");
            }
            sandboxServer.setServerAgentUrl(sandboxServerInfo.getScheme() + "://" + sandboxServerInfo.getHost() + ":" + sandboxServerInfo.getAgentPort());
            sandboxServer.setServerVncUrl(sandboxServerInfo.getScheme() + "://" + sandboxServerInfo.getHost() + ":" + sandboxServerInfo.getVncPort());
            sandboxServer.setServerFileUrl(sandboxServerInfo.getScheme() + "://" + sandboxServerInfo.getHost() + ":" + sandboxServerInfo.getFileServerPort());
            sandboxServer.setServerApiKey(sandboxServerInfo.getApiKey());
            sandboxServer.setMaxUsers(Integer.MAX_VALUE);
        }
        if (sandboxGlobalConfigDto != null) {
            try {
                sandboxServer.setPerUserMemoryGB(Double.parseDouble(sandboxGlobalConfigDto.getPerUserMemoryGB()));
                sandboxServer.setPerUserCpuCores(Integer.parseInt(sandboxGlobalConfigDto.getPerUserCpuCores()));
            } catch (NumberFormatException e) {
                sandboxServer.setPerUserMemoryGB(4);
                sandboxServer.setPerUserCpuCores(2);
            }
        } else {
            sandboxServer.setPerUserMemoryGB(4);
            sandboxServer.setPerUserCpuCores(2);
        }
        return sandboxServer;
    }

    @Override
    protected boolean execute(ScheduleTaskDto scheduleTaskDto) {
        try {
            this.checkServerStatus();
        } catch (Exception e) {
            log.error("定时任务执行失败", e);
        }
        return false;
    }

    public void checkServerStatus() {
        for (Map.Entry<Long, Map<String, ServerStatus>> entry : serverStatusMap.entrySet()) {
            TenantConfigDto tenantConfig = tenantConfigApplicationService.getTenantConfig(entry.getKey());
            if (tenantConfig != null) {
                try {
                    if (!JSON.isValid(tenantConfig.getSandboxConfig())) {
                        continue;
                    }
                    SandboxServerConfig sandboxServerConfig = buildGloabalSandboxServerConfig(tenantConfig);
                    Map<String, ServerStatus> serverStatusMap = entry.getValue();
                    for (SandboxServerConfig.SandboxServer sandboxServer : sandboxServerConfig.getSandboxServers()) {
                        ServerStatus serverStatus = serverStatusMap.get(sandboxServer.getServerId());
                        if (serverStatus == null) {
                            serverStatus = new ServerStatus();
                            serverStatusMap.put(sandboxServer.getServerId(), serverStatus);
                        }
                        try {
                            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(sandboxServer.getServerAgentUrl() + "/computer/pod/count"))
                                    .header("x-api-key", sandboxServer.getServerApiKey() == null ? "" : sandboxServer.getServerApiKey())
                                    .timeout(java.time.Duration.ofSeconds(10))
                                    .GET().build();
                            String serverStatusStr = httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
                            log.debug("url {}, serverStatusStr: {}", sandboxServer.getServerAgentUrl() + "/computer/pod/count", serverStatusStr);
                            JSONObject jsonObject = JSON.parseObject(serverStatusStr);
                            JSONObject data = jsonObject.getJSONObject("data");
                            serverStatus.currentUsers = data.getInteger("total_count");
                            serverStatus.setServerId(sandboxServer.getServerId());
                            serverStatus.setStatus(1);
                            serverStatus.setFailedTimes(0);
                        } catch (Exception e) {
                            if (serverStatus.getFailedTimes() > 3) {
                                serverStatus.setStatus(0);
                            }
                            log.warn("query server status error {}", sandboxServer.getServerAgentUrl(), e);
                        }

                    }
                } catch (Exception e) {
                    // ignore
                }
            }
        }
    }


    @Data
    private static class ServerStatus {
        private String serverId;
        private int status;
        private int currentUsers;
        private int failedTimes;
    }
}
