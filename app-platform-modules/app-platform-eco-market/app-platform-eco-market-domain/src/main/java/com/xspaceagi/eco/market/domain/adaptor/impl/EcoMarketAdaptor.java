package com.xspaceagi.eco.market.domain.adaptor.impl;

import java.util.Collections;
import java.util.List;

import com.xspaceagi.mcp.sdk.IMcpApiService;
import com.xspaceagi.mcp.sdk.dto.McpDto;

import org.springframework.stereotype.Service;

import com.xspaceagi.agent.core.sdk.IAgentRpcService;
import com.xspaceagi.agent.core.sdk.dto.AgentInfoDto;
import com.xspaceagi.agent.core.sdk.dto.PluginEnableOrUpdateDto;
import com.xspaceagi.agent.core.sdk.dto.TemplateEnableOrUpdateDto;
import com.xspaceagi.agent.core.sdk.enums.TargetTypeEnum;
import com.xspaceagi.eco.market.domain.adaptor.IEcoMarketAdaptor;
import com.xspaceagi.system.spec.exception.BizExceptionCodeEnum;
import com.xspaceagi.system.spec.exception.EcoMarketException;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class EcoMarketAdaptor implements IEcoMarketAdaptor {

    @Resource
    private IAgentRpcService agentRpcService;

    /**
     * MCP API服务
     */
    @Resource
    private IMcpApiService mcpApiService;

    @Override
    public List<AgentInfoDto> queryAgentInfoList(List<Long> agentIds) {
        try {
            log.info("调用Agent RPC服务查询Agent信息列表: agentIds={}", agentIds);
            if (agentIds == null || agentIds.isEmpty()) {
                return Collections.emptyList();
            }

            var result = agentRpcService.queryAgentInfoList(agentIds);
            if (result == null || !result.isSuccess()) {
                log.error("查询Agent信息列表失败: agentIds={}, errorCode={}, errorMsg={}",
                        agentIds, result != null ? result.getCode() : "NULL",
                        result != null ? result.getMessage() : "NULL");
                throw EcoMarketException.build(BizExceptionCodeEnum.ECO_MARKET_ERROR_8001, "查询Agent信息列表失败");
            }

            return result.getData() != null ? result.getData() : Collections.emptyList();
        } catch (EcoMarketException e) {
            // 已经是我们自定义的异常,直接抛出
            throw e;
        } catch (Exception e) {
            log.error("查询Agent信息列表异常: agentIds={}", agentIds, e);
            throw EcoMarketException.build(BizExceptionCodeEnum.ECO_MARKET_ERROR_8001,
                    "查询Agent信息列表异常: " + e.getMessage());
        }
    }

    @Override
    public String queryPluginConfig(Long pluginId, String paramJson) {
        try {
            log.info("调用Agent RPC服务查询插件配置: pluginId={}, paramJson={}", pluginId, paramJson);
            if (pluginId == null) {
                throw new IllegalArgumentException("插件ID不能为空");
            }

            var result = agentRpcService.queryPluginConfig(pluginId, paramJson);
            if (result == null || !result.isSuccess()) {
                log.error("查询插件配置失败: pluginId={}, errorCode={}, errorMsg={}",
                        pluginId, result != null ? result.getCode() : "NULL",
                        result != null ? result.getMessage() : "NULL");
                throw EcoMarketException.build(BizExceptionCodeEnum.ECO_MARKET_ERROR_8001, "查询插件配置失败");
            }

            return result.getData();
        } catch (EcoMarketException e) {
            throw e;
        } catch (Exception e) {
            log.error("查询插件配置异常: pluginId={}", pluginId, e);
            throw EcoMarketException.build(BizExceptionCodeEnum.ECO_MARKET_ERROR_8001, "查询插件配置异常: " + e.getMessage());
        }
    }

    @Override
    public Long pluginEnableOrUpdate(PluginEnableOrUpdateDto pluginEnableOrUpdateDto) {
        try {
            log.info("调用Agent RPC服务启用或更新插件: pluginEnableOrUpdateDto={}", pluginEnableOrUpdateDto);
            if (pluginEnableOrUpdateDto == null) {
                throw new IllegalArgumentException("插件启用或更新DTO不能为空");
            }

            var result = agentRpcService.pluginEnableOrUpdate(pluginEnableOrUpdateDto);
            if (result == null || !result.isSuccess()) {
                log.error("启用或更新插件失败: errorCode={}, errorMsg={}",
                        result != null ? result.getCode() : "NULL", result != null ? result.getMessage() : "NULL");
                throw EcoMarketException.build(BizExceptionCodeEnum.ECO_MARKET_ERROR_8001, "启用或更新插件失败");
            }

            return result.getData();
        } catch (EcoMarketException e) {
            throw e;
        } catch (Exception e) {
            log.error("启用或更新插件异常", e);
            throw EcoMarketException.build(BizExceptionCodeEnum.ECO_MARKET_ERROR_8001, "启用或更新插件异常: " + e.getMessage());
        }
    }

    @Override
    public Void disablePlugin(Long pluginId) {
        try {
            log.info("调用Agent RPC服务禁用插件: pluginId={}", pluginId);
            if (pluginId == null) {
                throw new IllegalArgumentException("插件ID不能为空");
            }

            var result = agentRpcService.disablePlugin(pluginId);
            if (result == null || !result.isSuccess()) {
                log.error("禁用插件失败: pluginId={}, errorCode={}, errorMsg={}",
                        pluginId, result != null ? result.getCode() : "NULL",
                        result != null ? result.getMessage() : "NULL");
                throw EcoMarketException.build(BizExceptionCodeEnum.ECO_MARKET_ERROR_8001, "禁用插件失败");
            }

            return null;
        } catch (EcoMarketException e) {
            throw e;
        } catch (Exception e) {
            log.error("禁用插件异常: pluginId={}", pluginId, e);
            throw EcoMarketException.build(BizExceptionCodeEnum.ECO_MARKET_ERROR_8001, "禁用插件异常: " + e.getMessage());
        }
    }

    @Override
    public String queryTemplateConfig(TargetTypeEnum targetType, Long targetId) {
        try {
            log.info("调用Agent RPC服务查询模板配置: targetType={}, targetId={}", targetType, targetId);
            if (targetType == null || targetId == null) {
                throw new IllegalArgumentException("目标类型或目标ID不能为空");
            }

            var result = agentRpcService.queryTemplateConfig(targetType, targetId);
            if (result == null || !result.isSuccess()) {
                log.error("查询模板配置失败: targetType={}, targetId={}, errorCode={}, errorMsg={}",
                        targetType, targetId, result != null ? result.getCode() : "NULL",
                        result != null ? result.getMessage() : "NULL");
                throw EcoMarketException.build(BizExceptionCodeEnum.ECO_MARKET_ERROR_8001, "查询模板配置失败");
            }

            return result.getData();
        } catch (EcoMarketException e) {
            throw e;
        } catch (Exception e) {
            log.error("查询模板配置异常: targetType={}, targetId={}", targetType, targetId, e);
            throw EcoMarketException.build(BizExceptionCodeEnum.ECO_MARKET_ERROR_8001, "查询模板配置异常: " + e.getMessage());
        }
    }

    @Override
    public Long templateEnableOrUpdate(TemplateEnableOrUpdateDto templateEnableOrUpdateDto) {
        try {
            log.info("调用Agent RPC服务启用或更新模板: templateEnableOrUpdateDto={}", templateEnableOrUpdateDto);
            if (templateEnableOrUpdateDto == null) {
                throw new IllegalArgumentException("模板启用或更新DTO不能为空");
            }

            var result = agentRpcService.templateEnableOrUpdate(templateEnableOrUpdateDto);
            if (result == null || !result.isSuccess()) {
                log.error("启用或更新模板失败: errorCode={}, errorMsg={}",
                        result != null ? result.getCode() : "NULL", result != null ? result.getMessage() : "NULL");
                throw EcoMarketException.build(BizExceptionCodeEnum.ECO_MARKET_ERROR_8001, "启用或更新模板失败");
            }

            return result.getData();
        } catch (EcoMarketException e) {
            throw e;
        } catch (Exception e) {
            log.error("启用或更新模板异常", e);
            throw EcoMarketException.build(BizExceptionCodeEnum.ECO_MARKET_ERROR_8001, "启用或更新模板异常: " + e.getMessage());
        }
    }

    @Override
    public Void disableTemplate(TargetTypeEnum targetType, Long targetId) {
        try {
            log.info("调用Agent RPC服务禁用模板: targetType={}, targetId={}", targetType, targetId);
            if (targetType == null || targetId == null) {
                throw new IllegalArgumentException("目标类型或目标ID不能为空");
            }

            var result = agentRpcService.disableTemplate(targetType, targetId);
            if (result == null || !result.isSuccess()) {
                log.error("禁用模板失败: targetType={}, targetId={}, errorCode={}, errorMsg={}",
                        targetType, targetId, result != null ? result.getCode() : "NULL",
                        result != null ? result.getMessage() : "NULL");
                throw EcoMarketException.build(BizExceptionCodeEnum.ECO_MARKET_ERROR_8001, "禁用模板失败");
            }

            return null;
        } catch (EcoMarketException e) {
            throw e;
        } catch (Exception e) {
            log.error("禁用模板异常: targetType={}, targetId={}", targetType, targetId, e);
            throw EcoMarketException.build(BizExceptionCodeEnum.ECO_MARKET_ERROR_8001, "禁用模板异常: " + e.getMessage());
        }
    }

    @Override
    public Long deployOfficialMcp(McpDto mcpDto) {
        log.info("调用MCP API服务部署MCP: mcpDto={}", mcpDto);
        if (mcpDto == null) {
            log.error("部署MCP失败: MCP配置不能为空");
            throw EcoMarketException.build(BizExceptionCodeEnum.ECO_MARKET_ERROR_8029);
        }

        var result = mcpApiService.deployOfficialMcp(mcpDto);
        if (result == null) {
            log.error("部署MCP失败: mcpDto={}", mcpDto);
            throw EcoMarketException.build(BizExceptionCodeEnum.ECO_MARKET_ERROR_8030);
        }

        return result;
    }

    @Override
    public Void stopOfficialMcp(Long targetId) {
        log.info("调用MCP API服务停止MCP: targetId={}", targetId);
        if (targetId == null) {
            log.error("停止MCP失败: MCP ID不能为空");
            throw EcoMarketException.build(BizExceptionCodeEnum.ECO_MARKET_ERROR_8031);
        }

        mcpApiService.stopOfficialMcp(targetId);
        return null;
    }
}
