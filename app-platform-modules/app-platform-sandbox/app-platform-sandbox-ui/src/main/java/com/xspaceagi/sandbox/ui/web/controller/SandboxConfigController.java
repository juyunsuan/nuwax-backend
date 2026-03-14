package com.xspaceagi.sandbox.ui.web.controller;

import com.xspaceagi.agent.core.adapter.application.AgentApplicationService;
import com.xspaceagi.agent.core.adapter.dto.UserAgentDto;
import com.xspaceagi.sandbox.application.dto.SandboxConfigDto;
import com.xspaceagi.sandbox.application.service.SandboxConfigApplicationService;
import com.xspaceagi.sandbox.infra.dao.vo.SandboxConfigValue;
import com.xspaceagi.sandbox.infra.dao.vo.SandboxServerInfo;
import com.xspaceagi.sandbox.spec.enums.SandboxScopeEnum;
import com.xspaceagi.sandbox.ui.web.dto.SandboxConfigCreateDto;
import com.xspaceagi.sandbox.ui.web.dto.SandboxRegDto;
import com.xspaceagi.sandbox.ui.web.dto.UserSandBoxSelectDto;
import com.xspaceagi.system.application.dto.TenantConfigDto;
import com.xspaceagi.system.application.dto.UserDto;
import com.xspaceagi.system.application.service.UserApplicationService;
import com.xspaceagi.system.infra.dao.entity.User;
import com.xspaceagi.system.spec.common.RequestContext;
import com.xspaceagi.system.spec.dto.ReqResult;
import com.xspaceagi.system.spec.utils.MD5;
import com.xspaceagi.system.spec.utils.RedisUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 沙盒配置管理接口
 */
@Tag(name = "用户沙盒相关接口")
@RestController
@RequestMapping("/api/sandbox/config")
public class SandboxConfigController {

    @Resource
    private SandboxConfigApplicationService sandboxConfigApplicationService;

    @Resource
    private UserApplicationService userApplicationService;

    @Resource
    private RedisUtil redisUtil;

    @Resource
    private AgentApplicationService agentApplicationService;

    @Value("${installation-source}")
    private String installationSource;

    static {
        // disable keep alive，暂不使用连接池
        System.setProperty("jdk.httpclient.keepalive.timeout", "0");
    }

    private final HttpClient httpClient = java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Operation(summary = "查询用户沙盒列表")
    @GetMapping("/list")
    public ReqResult<List<SandboxConfigDto>> listUserConfigs() {
        List<SandboxConfigDto> sandboxConfigs = sandboxConfigApplicationService.listUserConfigsByType(RequestContext.get().getUserId());
        sandboxConfigs.forEach(sandboxConfigDto -> {
            if (sandboxConfigDto.getConfigValue() != null && sandboxConfigDto.getConfigValue().getHostWithScheme() != null) {
                sandboxConfigDto.setConfigKey(null);
            }
        });
        return ReqResult.success(sandboxConfigs);
    }


    @Operation(summary = "用户可选沙盒选择列表")
    @GetMapping("/select/list")
    public ReqResult<UserSandBoxSelectDto> listUserSelectConfigs() {
        UserSandBoxSelectDto dto = new UserSandBoxSelectDto();
        List<SandboxConfigDto> sandboxConfigDtos = sandboxConfigApplicationService.listUserConfigsByType(RequestContext.get().getUserId()).stream().filter(SandboxConfigDto::isOnline).toList();
        Map<String, Object> stringObjectMap = redisUtil.hashGetAll("user-sandbox-selected:" + RequestContext.get().getUserId());
        Map<String, String> agentSelectedMap = new HashMap<>();
        if (stringObjectMap != null) {
            //移除stringObjectMap中value不在sandboxConfigDtos这里的项
            stringObjectMap.forEach((key, value) -> {
                if (sandboxConfigDtos.stream().anyMatch(sandboxConfigDto -> sandboxConfigDto.getId().equals(Long.parseLong(value.toString())))) {
                    agentSelectedMap.put(key, value.toString());
                }
            });
        }
        dto.setAgentSelected(agentSelectedMap);
        //过滤出在线的
        dto.setSandboxes(sandboxConfigDtos.stream().map(sandboxConfigDto -> {
            UserSandBoxSelectDto.SelectDto selectDto = new UserSandBoxSelectDto.SelectDto();
            selectDto.setSandboxId(sandboxConfigDto.getId().toString());
            selectDto.setName(sandboxConfigDto.getName());
            String notice = "在使用非自己创建的智能体时，请注意识别风险";
            if (StringUtils.isNotBlank(sandboxConfigDto.getDescription())) {
                selectDto.setDescription(sandboxConfigDto.getDescription() + "\n");
            } else {
                selectDto.setDescription(notice);
            }
            return selectDto;
        }).collect(Collectors.toList()));

        if ("saas".equals(installationSource) || sandboxConfigApplicationService.hasGlobalConfigsForSelect()) {
            UserSandBoxSelectDto.SelectDto selectDto = new UserSandBoxSelectDto.SelectDto();
            selectDto.setSandboxId("-1");
            selectDto.setName("云端电脑");
            selectDto.setDescription("由平台提供的智能体电脑");
            dto.getSandboxes().add(0, selectDto);
        }

        return ReqResult.success(dto);
    }

    @Operation(summary = "用户沙箱选中记录更新")
    @PostMapping("/selected/{agentId}/{sandboxId}")
    public ReqResult<Void> selected(@PathVariable Long agentId, @PathVariable String sandboxId) {
        redisUtil.hashPut("user-sandbox-selected:" + RequestContext.get().getUserId(), agentId.toString(), sandboxId);
        return ReqResult.success();
    }

    @Operation(summary = "客户端注册")
    @PostMapping("/reg")
    public ReqResult<SandboxConfigDto> create(@RequestBody SandboxRegDto sandboxRegDto, HttpServletRequest request) {
        Assert.notNull(sandboxRegDto.getSandboxConfigValue(), "终端配置信息不能为空");
        TenantConfigDto tenantConfigDto = (TenantConfigDto) RequestContext.get().getTenantConfig();
        String host = request.getHeader("Host");
        if (host != null) {
            host = host.split(":")[0];
            tenantConfigDto.setSiteUrl("https://" + host);//后续使用
        }
        if (StringUtils.isBlank(sandboxRegDto.getSandboxConfigValue().getHostWithScheme())) {
            sandboxRegDto.getSandboxConfigValue().setHostWithScheme("http://127.0.0.1");
        }
        if (StringUtils.isNotBlank(sandboxRegDto.getSavedKey())) {
            SandboxConfigDto byKey = sandboxConfigApplicationService.getByKey(sandboxRegDto.getSavedKey());
            if (byKey != null) {
                if (sandboxRegDto.getSandboxConfigValue().getVncPort() <= 0) {
                    sandboxRegDto.getSandboxConfigValue().setVncPort(6080);
                }
                SandboxConfigDto sandboxConfigUpdate = new SandboxConfigDto();
                sandboxConfigUpdate.setId(byKey.getId());
                sandboxConfigUpdate.setConfigValue(sandboxRegDto.getSandboxConfigValue());
                sandboxConfigApplicationService.update(sandboxConfigUpdate);
                byKey.setConfigValue(sandboxRegDto.getSandboxConfigValue());
                return ReqResult.success(byKey);
            }
        }
        Assert.isTrue(StringUtils.isNotBlank(sandboxRegDto.getUsername()), "用户名、邮箱或手机号码不能为空");
        Assert.isTrue(StringUtils.isNotBlank(sandboxRegDto.getPassword()), "动态认证吗或密码不能为空");
        String key = "sb:reg:" + tenantConfigDto.getTenantId() + ":" + MD5.MD5Encode(sandboxRegDto.getUsername());
        Long increment = redisUtil.increment(key, 1);
        if (increment <= 5) {
            redisUtil.expire(key, 1800);
        }
        Assert.isTrue(increment <= 5, "你已错误尝试了5次，账号将锁定半个小时");
        UserDto user;
        if (sandboxRegDto.getUsername().matches("^[a-zA-Z0-9._-]+@[a-zA-Z0-9_-]+(\\.[a-zA-Z0-9_-]+)+$")) {
            user = userApplicationService.queryUserByEmail(sandboxRegDto.getUsername());
            Assert.isTrue(user != null, "用户不存在或密码错误，失败5次将被锁定半小时，你已尝试" + increment + "次");
        } else {
            user = userApplicationService.queryUserByPhone(sandboxRegDto.getUsername());
            if (user == null) {
                user = userApplicationService.queryUserByUserName(sandboxRegDto.getUsername());
            }
            Assert.isTrue(user != null, "用户不存在或密码错误，失败5次将被锁定半小时，你已尝试" + increment + "次");
        }
        String userDynamicCode = userApplicationService.getUserDynamicCode(user.getId());
        if (!userDynamicCode.equals(sandboxRegDto.getPassword().trim())) {
            UserDto userDto = userApplicationService.queryUserByPhoneOrEmailWithPassword(sandboxRegDto.getUsername(), sandboxRegDto.getPassword().trim());
            Assert.isTrue(userDto != null, "用户不存在或认证码错误，失败5次将被锁定半小时，你已尝试" + increment + "次");
        }

        redisUtil.expire(key, 0);
        RequestContext.get().setUser(user);
        RequestContext.get().setUserId(user.getId());
        SandboxConfigDto dto = new SandboxConfigDto();
        dto.setUserId(user.getId());
        dto.setScope(SandboxScopeEnum.USER);
        dto.setConfigKey(UUID.randomUUID().toString().replace("-", ""));
        dto.setName("我的电脑");
        dto.setConfigValue(sandboxRegDto.getSandboxConfigValue());
        dto.setDescription("");
        dto.setIsActive(true);
        dto.setOnline(false);
        sandboxConfigApplicationService.create(dto, false);
        return ReqResult.success(sandboxConfigApplicationService.getByKey(dto.getConfigKey()));
    }

    @Operation(summary = "创建个人电脑（客户端配置）")
    @PostMapping("/create")
    public ReqResult<Void> create(@RequestBody SandboxConfigCreateDto configCreateDto) {
        Assert.notNull(configCreateDto.getName(), "名称不能为空");
        SandboxConfigDto dto = new SandboxConfigDto();
        dto.setName(configCreateDto.getName());
        dto.setDescription(configCreateDto.getDescription());
        dto.setUserId(RequestContext.get().getUserId());
        dto.setScope(SandboxScopeEnum.USER);
        dto.setConfigKey(UUID.randomUUID().toString().replace("-", ""));
        dto.setConfigValue(new SandboxConfigValue());
        sandboxConfigApplicationService.create(dto, true);
        return ReqResult.success();
    }

    @Operation(summary = "更新配置")
    @PostMapping("/update")
    public ReqResult<Void> update(@RequestBody SandboxConfigDto dto) {
        Assert.notNull(dto.getId(), "id不能为空");
        checkPermission(dto.getId());
        sandboxConfigApplicationService.update(dto);
        return ReqResult.success();
    }

    @Operation(summary = "删除配置")
    @PostMapping("/delete/{id}")
    public ReqResult<Void> delete(
            @Parameter(description = "配置ID") @PathVariable Long id) {
        checkPermission(id);
        sandboxConfigApplicationService.delete(id);
        return ReqResult.success();
    }

    @Operation(summary = "跳转到具体的电脑会话界面")
    @GetMapping("/redirect/{id}")
    public void redirect(HttpServletResponse response, @Parameter(description = "配置ID") @PathVariable Long id) {
        TenantConfigDto tenantConfigDto = (TenantConfigDto) RequestContext.get().getTenantConfig();
        String siteUrl = tenantConfigDto.getSiteUrl();
        siteUrl = siteUrl.trim().endsWith("/") ? siteUrl.trim() : siteUrl.trim() + "/";
        SandboxConfigDto byId = sandboxConfigApplicationService.getById(id);
        if (byId == null) {
            try {
                response.sendRedirect(siteUrl);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return;
        }

        String redirectUri;
        UserAgentDto userAgentDto = agentApplicationService.queryUserAgentRecentUse(byId.getUserId(), byId.getAgentId());
        if (userAgentDto != null && userAgentDto.getLastConversationId() != null) {
            redirectUri = "home/chat/" + userAgentDto.getLastConversationId() + "/" + byId.getAgentId();
        } else {
            redirectUri = "agent/" + byId.getAgentId();
        }
        try {
            response.sendRedirect(siteUrl + redirectUri);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    @Operation(summary = "客户端健康检查")
    @GetMapping("/health/{key}")
    public ReqResult<Object> health(@Parameter(description = "配置ID") @PathVariable String key) {
        SandboxConfigDto sandboxConfigDto = sandboxConfigApplicationService.getByKey(key);
        if (sandboxConfigDto == null) {
            return ReqResult.error("配置不存在");
        }
        if (sandboxConfigDto.getIsActive() != null && !sandboxConfigDto.getIsActive()) {
            return ReqResult.error("客户端在平台已被禁用");
        }
        if (!sandboxConfigDto.isOnline()) {
            return ReqResult.error("客户端已离线，请检查网络是否通畅");
        }
        SandboxServerInfo sandboxServerInfo = sandboxConfigDto.getServerInfo();
        if (sandboxServerInfo == null) {
            return ReqResult.error("配置信息错误");
        }
        String healthUrl = sandboxServerInfo.getScheme() + "://" + sandboxServerInfo.getHost() + ":" + sandboxServerInfo.getAgentPort() + "/health";
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(healthUrl))
                .header("x-api-key", sandboxConfigDto.getConfigValue().getApiKey() == null ? "" : sandboxConfigDto.getConfigValue().getApiKey())
                .timeout(java.time.Duration.ofSeconds(10))
                .GET().build();
        try {
            String serverStatusStr = httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
            return ReqResult.success(serverStatusStr);
        } catch (Exception e) {
            return ReqResult.error(e.getMessage());
        }
    }


    @Operation(summary = "启用/禁用配置")
    @PostMapping("/toggle/{id}")
    public ReqResult<Void> toggle(
            @Parameter(description = "配置ID") @PathVariable Long id) {
        checkPermission(id);
        sandboxConfigApplicationService.toggle(id);
        return ReqResult.success();
    }

    private void checkPermission(Long id) {
        UserDto user = (UserDto) RequestContext.get().getUser();
        if (user.getRole() == User.Role.Admin) {
            return;
        }
        SandboxConfigDto byId = sandboxConfigApplicationService.getById(id);
        Assert.isTrue(byId != null && byId.getUserId().equals(user.getId()), "无权限");
    }
}
