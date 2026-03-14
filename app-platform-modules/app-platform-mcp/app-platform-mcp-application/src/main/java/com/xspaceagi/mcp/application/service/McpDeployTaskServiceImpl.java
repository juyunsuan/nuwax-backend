package com.xspaceagi.mcp.application.service;

import com.alibaba.fastjson2.JSON;
import com.xspaceagi.mcp.adapter.application.McpDeployTaskService;
import com.xspaceagi.mcp.adapter.domain.McpConfigDomainService;
import com.xspaceagi.mcp.adapter.repository.entity.McpConfig;
import com.xspaceagi.mcp.infra.rpc.McpDeployRpcService;
import com.xspaceagi.mcp.infra.rpc.dto.McpDeployStatusResponse;
import com.xspaceagi.mcp.infra.rpc.enums.McpDeployStatusEnum;
import com.xspaceagi.mcp.infra.rpc.enums.McpPersistentTypeEnum;
import com.xspaceagi.mcp.sdk.dto.McpDto;
import com.xspaceagi.mcp.sdk.enums.DeployStatusEnum;
import com.xspaceagi.mcp.sdk.enums.InstallTypeEnum;
import com.xspaceagi.system.application.dto.SendNotifyMessageDto;
import com.xspaceagi.system.application.service.NotifyMessageApplicationService;
import com.xspaceagi.system.infra.dao.entity.NotifyMessage;
import com.xspaceagi.system.sdk.service.ScheduleTaskApiService;
import com.xspaceagi.system.sdk.service.TaskExecuteService;
import com.xspaceagi.system.sdk.service.dto.ScheduleTaskDto;
import com.xspaceagi.system.spec.common.RequestContext;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Date;
import java.util.Map;

@Slf4j
@Service("mcpDeployTaskService")
public class McpDeployTaskServiceImpl extends AbstractDeployTaskService implements McpDeployTaskService, TaskExecuteService {

    //每秒检查一次状态（5分钟没有部署成功就失败）
    private static final Long MAX_EXECUTE_TIMES = 300L;

    @Resource
    private ScheduleTaskApiService scheduleTaskApiService;

    @Resource
    private McpDeployRpcService mcpDeployRpcService;

    @Resource
    private McpConfigDomainService mcpConfigDomainService;

    @Resource
    private NotifyMessageApplicationService notifyMessageApplicationService;

    @Override
    public void addDeployTask(McpDto mcpDto) {
        addDeployTask(mcpDto, true);
    }

    @Override
    public void addDeployTask(McpDto mcpDto, boolean notify) {
        if (mcpDto.getInstallType() == InstallTypeEnum.COMPONENT) {
            McpConfig mcpConfig = new McpConfig();
            mcpConfig.setId(mcpDto.getId());
            mcpConfig.setConfig(JSON.toJSONString(mcpDto.getMcpConfig()));
            mcpConfig.setDeployedConfig(JSON.toJSONString(mcpDto.getMcpConfig()));
            mcpConfig.setDeployStatus(DeployStatusEnum.Deployed);
            mcpConfig.setDeployed(new Date());
            mcpConfigDomainService.update(mcpConfig);
            if (notify) {
                notifyMessageApplicationService.sendNotifyMessage(SendNotifyMessageDto.builder()
                        .scope(NotifyMessage.MessageScope.System)
                        .content("你创建的MCP服务[" + mcpDto.getName() + "]已部署成功")
                        .userIds(Arrays.asList(mcpDto.getCreatorId()))
                        .build());
            }
            return;
        }
        String taskId = "mcp:" + mcpDto.getId() + ":" + System.currentTimeMillis();
        scheduleTaskApiService.start(ScheduleTaskDto.builder()
                .taskId(taskId)
                .beanId("mcpDeployTaskService")
                .maxExecTimes(MAX_EXECUTE_TIMES)
                .cron(ScheduleTaskDto.Cron.EVERY_SECOND.getCron())
                .params(Map.of("id", mcpDto.getId(),
                        "tenantId", RequestContext.get().getTenantId(),
                        "userId", mcpDto.getCreatorId(),
                        "name", mcpDto.getName(),
                        "description", mcpDto.getDescription(),
                        "installType", mcpDto.getInstallType().name(),
                        "mcpConfig", mcpDto.getMcpConfig().getServerConfig()))
                .build());
        McpConfig mcpConfig = new McpConfig();
        mcpConfig.setId(mcpDto.getId());
        mcpConfig.setDeployStatus(DeployStatusEnum.Deploying);
        mcpConfigDomainService.update(mcpConfig);
    }


    @Override
    public Mono<Boolean> asyncExecute(ScheduleTaskDto scheduleTask) {
        return Mono.fromCallable(() -> execute1(scheduleTask));
    }

    public boolean execute1(ScheduleTaskDto scheduleTask) {
        log.info("部署MCP {}", scheduleTask.getParams());
        Map<String, Object> param = (Map<String, Object>) scheduleTask.getParams();
        if (param.get("tenantId") == null) {
            return true;
        }
        Long tenantId = Long.parseLong(param.get("tenantId").toString());
        RequestContext.set(RequestContext.builder()
                .tenantId(tenantId)
                .build());
        try {
            return execute0(scheduleTask);
        } finally {
            RequestContext.remove();
        }
    }

    private boolean execute0(ScheduleTaskDto scheduleTask) {
        log.info("部署MCP {}", scheduleTask.getParams());
        Map<String, Object> param = (Map<String, Object>) scheduleTask.getParams();
        Long id = Long.parseLong(param.get("id").toString());
        Long userId = Long.parseLong(param.get("userId").toString());
        String serverConfig = param.get("mcpConfig").toString();
        String mcpName = param.get("name").toString();
        boolean isSSE = "SSE".equals(param.get("installType"));
        McpDeployStatusResponse deployStatus;
        if (isSSE) {
            deployStatus = new McpDeployStatusResponse();
            deployStatus.setStatus(McpDeployStatusEnum.Ready);
        } else {
            deployStatus = mcpDeployRpcService.deploy(String.valueOf(id), serverConfig, McpPersistentTypeEnum.OneShot);
        }
        if (deployStatus.getStatus() == McpDeployStatusEnum.Ready) {
            try {
                updateAndSaveMcpConfig(id, serverConfig, isSSE, null);
            } catch (Exception e) {
                return false;
            }
            if (userId != null) {
                notifyMessageApplicationService.sendNotifyMessage(SendNotifyMessageDto.builder()
                        .scope(NotifyMessage.MessageScope.System)
                        .content("你创建的MCP服务[" + mcpName + "]已部署成功")
                        .userIds(Arrays.asList(userId))
                        .build());
            }
            log.info("MCP服务[" + mcpName + "]已部署成功");
            return true;
        }
        if (deployStatus.getStatus() == McpDeployStatusEnum.Error) {
            McpConfig mcpConfig = new McpConfig();
            mcpConfig.setId(id);
            mcpConfig.setDeployStatus(DeployStatusEnum.DeployFailed);
            mcpConfigDomainService.update(mcpConfig);
            if (userId != null) {
                notifyMessageApplicationService.sendNotifyMessage(SendNotifyMessageDto.builder()
                        .scope(NotifyMessage.MessageScope.System)
                        .content("你创建的MCP服务[" + mcpName + "]部署失败，请修改配置后重新提交部署\n```\n" + deployStatus.getMessage() + "\n```")
                        .userIds(Arrays.asList(userId))
                        .build());
            }
            return true;
        }
        if (scheduleTask.getExecTimes() + 1 >= scheduleTask.getMaxExecTimes()) {
            McpConfig mcpConfig = new McpConfig();
            mcpConfig.setId(id);
            mcpConfig.setDeployStatus(DeployStatusEnum.DeployFailed);
            mcpConfigDomainService.update(mcpConfig);
            if (userId != null) {
                notifyMessageApplicationService.sendNotifyMessage(SendNotifyMessageDto.builder()
                        .scope(NotifyMessage.MessageScope.System)
                        .content("你创建的MCP服务[" + mcpName + "]部署超时，请检查配置后重新提交部署")
                        .userIds(Arrays.asList(userId))
                        .build());
            }
        }
        return false;
    }
}
