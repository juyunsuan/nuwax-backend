package com.xspaceagi.interceptor;

import com.xspaceagi.agent.core.adapter.application.AgentApplicationService;
import com.xspaceagi.agent.core.adapter.dto.AgentDetailDto;
import com.xspaceagi.system.application.dto.TenantConfigDto;
import com.xspaceagi.system.application.dto.UserDto;
import com.xspaceagi.system.application.service.UserApplicationService;
import com.xspaceagi.system.infra.dao.entity.User;
import com.xspaceagi.system.sdk.service.UserAccessKeyApiService;
import com.xspaceagi.system.sdk.service.dto.UserAccessKeyDto;
import com.xspaceagi.system.spec.common.RequestContext;
import com.xspaceagi.system.spec.enums.ErrorCodeEnum;
import com.xspaceagi.system.spec.enums.YesOrNoEnum;
import com.xspaceagi.system.spec.exception.BizException;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

@Component
public class ApiKeyInterceptor implements HandlerInterceptor {

    @Resource
    private UserApplicationService userApplicationService;

    @Resource
    private AgentApplicationService agentApplicationService;

    @Resource
    private UserAccessKeyApiService userAccessKeyApiService;

    /**
     * 请求处理完之后
     */
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object obj, Exception exc) throws Exception {
        RequestContext.remove();
    }

    /**
     * 请求处理完成
     */
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object obj, ModelAndView model) throws Exception {
    }

    /**
     * 请求处理之前
     */
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String authorization = request.getHeader("Authorization");
        if (authorization != null) {
            authorization = authorization.replaceFirst("Basic", "").replaceFirst("Bearer", "").trim();
        }
        //是否为ak校验
        if (authorization != null && (authorization.startsWith("ak-") || authorization.startsWith("ck-"))) {
            UserAccessKeyDto userAccessKeyDto = userAccessKeyApiService.queryAccessKey(authorization);
            if (userAccessKeyDto == null) {
                throw new BizException(ErrorCodeEnum.INVALID_PARAM.getCode(), "Invalid API Key");
            }
            if (!completeAuthContext(request, userAccessKeyDto)) {
                throw new BizException(ErrorCodeEnum.PERMISSION_DENIED.getCode(), "API Key does not have access permissions.");
            }
            return true;
        }
        if (request.getRequestURI().startsWith("/api/v1/")) {
            throw new BizException(ErrorCodeEnum.PERMISSION_DENIED.getCode(), "Missing API Key");
        }
        return true;
    }

    private boolean completeAuthContext(HttpServletRequest request, UserAccessKeyDto userAccessKeyDto) {
        UserDto userDto = userApplicationService.queryById(userAccessKeyDto.getUserId());
        RequestContext.get().setUserId(userDto.getId());
        RequestContext.get().setUser(userDto);
        RequestContext.get().setLogin(true);
        RequestContext.get().setUserAccessKey(userAccessKeyDto);
        if (userAccessKeyDto.getTargetType() == UserAccessKeyDto.AKTargetType.Agent) {
            if (request.getRequestURI().startsWith("/api/v1/") || request.getRequestURI().startsWith("/api/file/")) {
                TenantConfigDto tenantConfigDto = (TenantConfigDto) RequestContext.get().getTenantConfig();
                if (tenantConfigDto.getAllowAgentApi() != null && tenantConfigDto.getAllowAgentApi().equals(YesOrNoEnum.N.getKey()) && userDto.getRole() != User.Role.Admin) {
                    throw new BizException(ErrorCodeEnum.PERMISSION_DENIED.getCode(), "The API calls are currently disabled.");
                }
                AgentDetailDto agentDetailDto = agentApplicationService.queryAgentDetail(Long.parseLong(userAccessKeyDto.getTargetId()), false);
                RequestContext.get().setAkTarget(agentDetailDto);
                return true;
            }
        }

        // 允许沙箱访问的接口范围
        if (userAccessKeyDto.getTargetType() == UserAccessKeyDto.AKTargetType.Sandbox && request.getRequestURI().startsWith("/api/v1/4sandbox")) {
            return true;
        }
        return userAccessKeyDto.getTargetType() == UserAccessKeyDto.AKTargetType.Tenant;
    }
}