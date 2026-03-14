package com.xspaceagi.custompage.ui.web.controller;

import java.net.InetAddress;
import java.util.List;

import com.xspaceagi.system.spec.annotation.RequireResource;
import com.xspaceagi.system.spec.cache.SimpleJvmHashCache;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xspaceagi.custompage.application.service.ICustomPageConfigApplicationService;
import com.xspaceagi.custompage.application.service.ICustomPageDomainApplicationService;
import com.xspaceagi.custompage.domain.model.CustomPageConfigModel;
import com.xspaceagi.custompage.domain.model.CustomPageDomainModel;
import com.xspaceagi.custompage.ui.web.dto.CustomPageDomainCreateReq;
import com.xspaceagi.custompage.ui.web.dto.CustomPageDomainDeleteReq;
import com.xspaceagi.custompage.ui.web.dto.CustomPageDomainUpdateReq;
import com.xspaceagi.system.sdk.permission.SpacePermissionService;
import com.xspaceagi.system.spec.common.UserContext;
import com.xspaceagi.system.spec.dto.ReqResult;
import com.xspaceagi.system.spec.exception.SpacePermissionException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

import static com.xspaceagi.system.spec.enums.ResourceEnum.PAGE_APP_BIND_DOMAIN;

@Tag(name = "自定义页面域名绑定", description = "自定义页面域名绑定相关接口")
@RestController
@RequestMapping("/api/custom-page/domain")
@Slf4j
public class CustomPageDomainController extends BaseController {

    @Resource
    private ICustomPageDomainApplicationService customPageDomainApplicationService;

    @Resource
    private ICustomPageConfigApplicationService customPageConfigApplicationService;

    @Resource
    private SpacePermissionService spacePermissionService;

    @Value("${custom-page.cnames:}")
    private List<String> cnames;

    /**
     * 根据project_id查询域名列表
     * GET /api/custom-page-domain/list
     */
    @RequireResource(PAGE_APP_BIND_DOMAIN)
    @Operation(summary = "根据project_id查询域名列表")
    @GetMapping(value = "/list", produces = MediaType.APPLICATION_JSON_VALUE)
    public ReqResult<List<CustomPageDomainModel>> listByProject(@RequestParam("projectId") Long projectId) {
        log.info("[listByProject] 查询域名列表, projectId={}", projectId);
        try {
            // 校验项目权限
            CustomPageConfigModel configModel = customPageConfigApplicationService.getByProjectId(projectId);
            if (configModel == null) {
                return ReqResult.error("0001", "项目不存在");
            }
            spacePermissionService.checkSpaceUserPermission(configModel.getSpaceId());

            List<CustomPageDomainModel> result = customPageDomainApplicationService.listByProjectId(projectId);

            log.info("[listByProject] 查询域名列表成功, projectId={}, count={}", projectId, result.size());
            return ReqResult.success(result);
        } catch (SpacePermissionException e) {
            log.error("[listByProject] 查询域名列表失败, projectId={}, {}", projectId, e.getMessage());
            return ReqResult.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("[listByProject] 查询域名列表异常, projectId={}", projectId, e);
            return ReqResult.error("0000", "查询失败: " + e.getMessage());
        }
    }

    /**
     * 新增域名绑定
     * POST /api/custom-page-domain/create
     */
    @RequireResource(PAGE_APP_BIND_DOMAIN)
    @Operation(summary = "新增域名绑定")
    @PostMapping(value = "/create", produces = MediaType.APPLICATION_JSON_VALUE)
    public ReqResult<CustomPageDomainModel> create(@RequestBody CustomPageDomainCreateReq req) {
        log.info("[create] 创建域名绑定, projectId={}, domain={}", req.getProjectId(), req.getDomain());
        try {
            //写个正则校验域名合法性
            if (!req.getDomain().matches("^([a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,6}$")) {
                return ReqResult.error("0001", "域名格式错误");
            }
            if (CollectionUtils.isNotEmpty(cnames)) {
                boolean isBindOfficialCname = false;
                for (String cname : cnames) {
                    String cnameIp = resolveDns(cname);
                    String ip = resolveDns(req.getDomain());
                    if (cnameIp.equals(ip)) {
                        isBindOfficialCname = true;
                        break;
                    }
                }
                if (!isBindOfficialCname) {
                    return ReqResult.error("0001", "请将域名解析到官方指定的CNAME上，10分钟后再尝试绑定");
                }
            }
            // 校验项目权限
            CustomPageConfigModel configModel = customPageConfigApplicationService.getByProjectId(req.getProjectId());
            if (configModel == null) {
                return ReqResult.error("0001", "项目不存在");
            }
            spacePermissionService.checkSpaceUserPermission(configModel.getSpaceId());

            UserContext userContext = getUser();

            CustomPageDomainModel model = new CustomPageDomainModel();
            model.setProjectId(req.getProjectId());
            model.setDomain(req.getDomain());

            ReqResult<CustomPageDomainModel> result = customPageDomainApplicationService.create(model, userContext);

            log.info("[create] 创建域名绑定完成, projectId={}, domain={}, result={}",
                    req.getProjectId(), req.getDomain(), result.isSuccess());
            return result;
        } catch (SpacePermissionException e) {
            log.error("[create] 创建域名绑定失败, projectId={}, domain={}, {}", req.getProjectId(), req.getDomain(), e.getMessage());
            return ReqResult.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("[create] 创建域名绑定异常, projectId={}, domain={}", req.getProjectId(), req.getDomain(), e);
            return ReqResult.error("0000", "创建失败: " + e.getMessage());
        }
    }

    /**
     * 修改域名绑定
     * POST /api/custom-page-domain/update
     */
    @RequireResource(PAGE_APP_BIND_DOMAIN)
    @Operation(summary = "修改域名绑定")
    @PostMapping(value = "/update", produces = MediaType.APPLICATION_JSON_VALUE)
    public ReqResult<CustomPageDomainModel> update(@RequestBody CustomPageDomainUpdateReq req) {
        log.info("[update] 更新域名绑定, id={},  domain={}", req.getId(), req.getDomain());
        try {
            // 校验原记录的权限：通过id查询现有域名绑定
            CustomPageDomainModel existingDomain = customPageDomainApplicationService.getById(req.getId());
            if (existingDomain == null) {
                return ReqResult.error("0002", "域名绑定不存在");
            }

            // 校验原projectId对应的项目权限
            CustomPageConfigModel existingConfigModel = customPageConfigApplicationService.getByProjectId(existingDomain.getProjectId());
            if (existingConfigModel == null) {
                return ReqResult.error("0001", "原项目不存在");
            }
            spacePermissionService.checkSpaceUserPermission(existingConfigModel.getSpaceId());

            UserContext userContext = getUser();

            CustomPageDomainModel model = new CustomPageDomainModel();
            model.setId(req.getId());
            model.setDomain(req.getDomain());

            ReqResult<CustomPageDomainModel> result = customPageDomainApplicationService.update(model, userContext);

            log.info("[update] 更新域名绑定完成, id={}, result={}", req.getId(), result.isSuccess());
            return result;
        } catch (SpacePermissionException e) {
            log.error("[update] 更新域名绑定失败, id={}, {}", req.getId(), e.getMessage());
            return ReqResult.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("[update] 更新域名绑定异常, id={}", req.getId(), e);
            return ReqResult.error("0000", "更新失败: " + e.getMessage());
        }
    }

    /**
     * 删除域名绑定
     * POST /api/custom-page-domain/delete
     */
    @RequireResource(PAGE_APP_BIND_DOMAIN)
    @Operation(summary = "删除域名绑定")
    @PostMapping(value = "/delete", produces = MediaType.APPLICATION_JSON_VALUE)
    public ReqResult<Void> delete(@RequestBody CustomPageDomainDeleteReq req) {
        log.info("[delete] 删除域名绑定, id={}", req.getId());
        try {
            // 校验域名绑定是否存在并获取项目ID
            CustomPageDomainModel targetDomain = customPageDomainApplicationService.getById(req.getId());
            if (targetDomain == null) {
                return ReqResult.error("0002", "域名绑定不存在");
            }

            // 校验项目权限
            CustomPageConfigModel configModel = customPageConfigApplicationService.getByProjectId(targetDomain.getProjectId());
            if (configModel == null) {
                return ReqResult.error("0001", "项目不存在");
            }
            spacePermissionService.checkSpaceUserPermission(configModel.getSpaceId());

            UserContext userContext = getUser();

            ReqResult<Void> result = customPageDomainApplicationService.delete(req.getId(), userContext);

            log.info("[delete] 删除域名绑定完成, id={}, result={}", req.getId(), result.isSuccess());
            return result;
        } catch (SpacePermissionException e) {
            log.error("[delete] 删除域名绑定失败, id={}, {}", req.getId(), e.getMessage());
            return ReqResult.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("[delete] 删除域名绑定异常, id={}", req.getId(), e);
            return ReqResult.error("0000", "删除失败: " + e.getMessage());
        }
    }

    private static String resolveDns(String domain) {
        Object dns = SimpleJvmHashCache.getHash("dns", domain);
        if (dns != null) {
            return dns.toString();
        }
        String ip = domain;
        try {
            InetAddress address = InetAddress.getByName(domain);
            ip = address.getHostAddress();
        } catch (Exception e) {
            return "";
        }
        SimpleJvmHashCache.putHash("dns", domain, ip, 600);
        return ip;
    }
}
