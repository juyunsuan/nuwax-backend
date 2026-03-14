package com.xspaceagi.custompage.ui.web.controller;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.xspaceagi.system.spec.annotation.RequireResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xspaceagi.agent.core.adapter.application.PluginApplicationService;
import com.xspaceagi.agent.core.adapter.application.PublishApplicationService;
import com.xspaceagi.agent.core.adapter.application.WorkflowApplicationService;
import com.xspaceagi.agent.core.adapter.dto.PublishedPermissionDto;
import com.xspaceagi.agent.core.adapter.repository.entity.Published;
import com.xspaceagi.custompage.application.service.ICustomPageBuildApplicationService;
import com.xspaceagi.custompage.application.service.ICustomPageConfigApplicationService;
import com.xspaceagi.custompage.domain.model.CustomPageBuildModel;
import com.xspaceagi.custompage.domain.model.CustomPageConfigModel;
import com.xspaceagi.custompage.domain.proxypath.ICustomPageProxyPathService;
import com.xspaceagi.custompage.sdk.ICustomPageRpcService;
import com.xspaceagi.custompage.sdk.dto.CopyTypeEnum;
import com.xspaceagi.custompage.sdk.dto.CustomPageDto;
import com.xspaceagi.custompage.sdk.dto.CustomPageQueryReq;
import com.xspaceagi.custompage.sdk.dto.DataSourceDto;
import com.xspaceagi.custompage.sdk.dto.DataTypeEnum;
import com.xspaceagi.custompage.sdk.dto.InputTypeEnum;
import com.xspaceagi.custompage.sdk.dto.PageArg;
import com.xspaceagi.custompage.sdk.dto.PageArgConfig;
import com.xspaceagi.custompage.sdk.dto.ProjectType;
import com.xspaceagi.custompage.sdk.dto.ProxyConfig;
import com.xspaceagi.custompage.sdk.dto.ProxyConfigBackend;
import com.xspaceagi.custompage.ui.web.dto.BindDataSourceReq;
import com.xspaceagi.custompage.ui.web.dto.CustomPageCopyReq;
import com.xspaceagi.custompage.ui.web.dto.CustomPageCreateReq;
import com.xspaceagi.custompage.ui.web.dto.CustomPageCreateRes;
import com.xspaceagi.custompage.ui.web.dto.CustomPageDeleteReq;
import com.xspaceagi.custompage.ui.web.dto.CustomPageUpdateReq;
import com.xspaceagi.custompage.ui.web.dto.DeletePathReq;
import com.xspaceagi.custompage.ui.web.dto.PageArgConfigReq;
import com.xspaceagi.custompage.ui.web.dto.ProjectContentRes;
import com.xspaceagi.custompage.ui.web.dto.ProxyConfigBatchReq;
import com.xspaceagi.system.sdk.permission.SpacePermissionService;
import com.xspaceagi.system.spec.common.UserContext;
import com.xspaceagi.system.spec.dto.ReqResult;
import com.xspaceagi.system.spec.enums.YesOrNoEnum;
import com.xspaceagi.system.spec.exception.BizException;
import com.xspaceagi.system.spec.exception.SpacePermissionException;
import com.xspaceagi.system.spec.page.PageQueryVo;
import com.xspaceagi.system.spec.page.SuperPage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static com.xspaceagi.system.spec.enums.ResourceEnum.*;

@Tag(name = "网页应用", description = "网页应用相关接口")
@RestController
@RequestMapping("/api/custom-page")
@Slf4j
@RequiredArgsConstructor
public class CustomPageConfigController extends BaseController {

    @Resource
    private PluginApplicationService pluginApplicationService;
    @Resource
    private WorkflowApplicationService workflowApplicationService;
    @Resource
    private SpacePermissionService spacePermissionService;
    @Resource
    private ICustomPageRpcService iCustomPageRpcService;
    @Resource
    private PublishApplicationService publishApplicationService;
    @Resource
    private ICustomPageConfigApplicationService customPageConfigApplicationService;
    @Resource
    private ICustomPageProxyPathService customPageProxyPathApplicationService;
    @Resource
    private ICustomPageBuildApplicationService customPageBuildApplicationService;

    @RequireResource(PAGE_APP_CREATE)
    @Operation(summary = "创建项目", description = "创建项目")
    @PostMapping(value = "/create", produces = MediaType.APPLICATION_JSON_VALUE)
    public ReqResult<CustomPageCreateRes> create(@RequestBody CustomPageCreateReq req) {
        log.info("[Web] 接收到创建项目请求，projectName={}", req.getProjectName());
        try {
            UserContext userContext = getUser();

            // 创建项目
            CustomPageConfigModel model = new CustomPageConfigModel();
            model.setName(req.getProjectName());
            model.setDescription(req.getProjectDesc());
            model.setSpaceId(req.getSpaceId());
            model.setIcon(req.getIcon());
            model.setCoverImg(req.getCoverImg());
            model.setCoverImgSourceType(req.getCoverImgSourceType());
            model.setNeedLogin(YesOrNoEnum.Y.getKey());
            model.setProjectType(ProjectType.ONLINE_DEPLOY);

            var result = customPageConfigApplicationService.create(model, userContext);
            if (!result.isSuccess()) {
                return ReqResult.create(result.getCode(), result.getMessage(), null);
            }
            log.info("[Web] 创建项目成功,开始初始化,projectId={}", result.getData().getId());

            // 初始化项目
            ReqResult<Map<String, Object>> initResult = customPageBuildApplicationService
                    .initProject(result.getData().getId(), userContext);
            log.info("[Web] projectId={},初始化项目结果,{}:{}, resp={}",
                    result.getData().getId(), initResult.getCode(), initResult.getMessage(), initResult.getData());

            if (!initResult.isSuccess()) {
                log.warn("[Web] projectId={},初始化项目失败,开始删除项目",
                        result.getData().getId());
                customPageConfigApplicationService.deleteProject(result.getData().getId(), userContext);
                return ReqResult.error("9999", initResult.getMessage());
            }

            CustomPageCreateRes res = CustomPageCreateRes.builder().projectId(result.getData().getId()).build();
            return ReqResult.success(res);
        } catch (SpacePermissionException e) {
            log.error("[Web] 创建项目失败，projectName={}, {}", req.getProjectName(), e.getMessage());
            return ReqResult.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("[Web] 创建项目失败，projectName={}", req.getProjectName(), e);
            return ReqResult.error("0001", e.getMessage());
        }
    }

    @RequireResource(PAGE_APP_IMPORT)
    @Operation(summary = "上传项目", description = "上传zip，创建项目")
    @PostMapping(value = "/upload-and-start", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ReqResult<CustomPageCreateRes> uploadProject(@ModelAttribute CustomPageCreateReq req) {
        log.info("[Web] 接收到上传项目请求，projectName={}, projectId={}", req.getProjectName(), req.getProjectId());
        try {
            UserContext userContext = getUser();
            CustomPageConfigModel configModel = null;
            boolean isInitProject = true;

            // 上传要先判断项目是否存在
            if (req.getProjectId() != null && req.getProjectId() > 0) {
                configModel = customPageConfigApplicationService.getByProjectId(req.getProjectId());
                if (configModel == null) {
                    return ReqResult.error("0001", "项目不存在");
                }
                isInitProject = false;
            } else {
                // 创建新项目
                CustomPageConfigModel model = new CustomPageConfigModel();
                model.setName(req.getProjectName());
                model.setDescription(req.getProjectDesc());
                model.setSpaceId(req.getSpaceId());
                model.setIcon(req.getIcon());
                model.setCoverImg(req.getCoverImg());
                model.setCoverImgSourceType(req.getCoverImgSourceType());
                model.setNeedLogin(YesOrNoEnum.Y.getKey());
                model.setProjectType(ProjectType.ONLINE_DEPLOY);

                var result = customPageConfigApplicationService.create(model, userContext);
                if (!result.isSuccess()) {
                    return ReqResult.create(result.getCode(), result.getMessage(), null);
                }
                configModel = result.getData();
            }

            var result = customPageConfigApplicationService.uploadProject(configModel, req.getFile(), isInitProject,
                    userContext);
            if (!result.isSuccess()) {
                if (isInitProject) {
                    log.warn("[Web] projectId={},上传项目失败,开始删除项目", configModel.getId());
                    customPageConfigApplicationService.deleteProject(configModel.getId(), userContext);
                }
                return ReqResult.error("9999", "上传项目失败: " + result.getMessage());
            }

            CustomPageCreateRes res = CustomPageCreateRes.builder().projectId(configModel.getId()).build();
            if (result.getData().get("port") != null) {
                try {
                    res.setDevServerUrl(customPageProxyPathApplicationService.getDevProxyPath(configModel.getId()));
                } catch (Exception e) {
                    // 不抛出异常,前端上传成功即可
                }
            }

            return ReqResult.success(res);
        } catch (SpacePermissionException e) {
            log.error("[Web] 上传项目失败，projectName={}, projectId={}, {}", req.getProjectName(), req.getProjectId(),
                    e.getMessage());
            return ReqResult.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("[Web] 上传项目异常，projectName={}, projectId={}", req.getProjectName(), req.getProjectId(), e);
            return ReqResult.error("0001", e.getMessage());
        }
    }

    @RequireResource(PAGE_APP_MODIFY)
    @Operation(summary = "修改项目", description = "修改项目基本信息")
    @PostMapping(value = "/update-project", produces = MediaType.APPLICATION_JSON_VALUE)
    public ReqResult<Void> updateProject(@RequestBody CustomPageUpdateReq req) {
        log.info("[Web] 接收到修改项目请求，projectId={}, projectName={}", req.getProjectId(), req.getProjectName());
        try {
            UserContext userContext = getUser();

            CustomPageConfigModel model = new CustomPageConfigModel();
            model.setId(req.getProjectId());
            model.setName(req.getProjectName());
            model.setDescription(req.getProjectDesc());
            model.setIcon(req.getIcon());
            model.setCoverImg(req.getCoverImg());
            model.setCoverImgSourceType(req.getCoverImgSourceType());
            model.setNeedLogin(req.getNeedLogin() == null ? null
                    : req.getNeedLogin() ? YesOrNoEnum.Y.getKey() : YesOrNoEnum.N.getKey());

            var result = customPageConfigApplicationService.updateProject(model, userContext);
            if (!result.isSuccess()) {
                log.warn("[Web] projectId={},修改项目失败,message={}", req.getProjectId(), result.getMessage());
                return ReqResult.create(result.getCode(), result.getMessage(), null);
            }

            return ReqResult.success();
        } catch (SpacePermissionException e) {
            log.error("[Web] 修改项目失败，projectId={}, projectName={}, {}", req.getProjectId(), req.getProjectName(),
                    e.getMessage());
            return ReqResult.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("[Web] 修改项目失败，projectId={}, projectName={}", req.getProjectId(), req.getProjectName(), e);
            return ReqResult.error("0001", e.getMessage());
        }
    }

    @RequireResource(PAGE_APP_DELETE)
    @Operation(summary = "删除项目", description = "删除项目")
    @PostMapping(value = "/delete-project", produces = MediaType.APPLICATION_JSON_VALUE)
    public ReqResult<Void> deleteProject(@RequestBody CustomPageDeleteReq req) {
        log.info("[Web] 接收到删除项目请求，projectId={}", req.getProjectId());
        try {
            UserContext userContext = getUser();

            var result = customPageConfigApplicationService.deleteProject(req.getProjectId(), userContext);
            if (!result.isSuccess()) {
                log.warn("[Web] projectId={},删除项目失败,message={}", req.getProjectId(), result.getMessage());
                return ReqResult.create(result.getCode(), result.getMessage(), null);
            }

            Map<String, Object> data = result.getData();
            CustomPageConfigModel config = (CustomPageConfigModel) data.get("config");
            CustomPageBuildModel build = (CustomPageBuildModel) data.get("build");
            // 删除远程的项目文件
            if (config.getProjectType() == ProjectType.ONLINE_DEPLOY) {
                customPageBuildApplicationService.deleteProjectFiles(build, userContext);
            }

            return ReqResult.success();
        } catch (SpacePermissionException e) {
            log.error("[Web] 删除项目失败，projectId={}, {}", req.getProjectId(), e.getMessage());
            return ReqResult.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("[Web] 删除项目失败，projectId={}", req.getProjectId(), e);
            return ReqResult.error("0001", e.getMessage());
        }
    }

    @RequireResource(PAGE_APP_COPY_TO_SPACE)
    @Operation(summary = "复制项目", description = "复制项目到目标空间")
    @PostMapping(value = "/copy-project", produces = MediaType.APPLICATION_JSON_VALUE)
    public ReqResult<CustomPageCreateRes> copyProject(@RequestBody CustomPageCopyReq req) {
        log.info("[Web] 接收到复制项目请求，projectId={}, targetSpaceId={}", req.getProjectId(), req.getTargetSpaceId());
        Long projectId = req.getProjectId();
        try {
            UserContext userContext = getUser();

            CustomPageConfigModel sourceConfig = customPageConfigApplicationService.getByProjectId(projectId);
            if (sourceConfig == null) {
                log.error("[copyProject] 源项目不存在, projectId={}", projectId);
                return ReqResult.error("0001", "源项目不存在");
            }
            Long devAgentId = sourceConfig.getDevAgentId();
            Long targetSpaceId = req.getTargetSpaceId();

            //校验目标空间权限
            spacePermissionService.checkSpaceUserPermission(targetSpaceId);

            CopyTypeEnum copyType = req.getCopyType();
            if (copyType == null || copyType == CopyTypeEnum.SQUARE) {
                //广场复制
                //校验项目复制权限
                PublishedPermissionDto permissionDto = publishApplicationService.hasPermission(Published.TargetType.Agent, devAgentId);
                if (!permissionDto.isCopy()) {
                    throw new SpacePermissionException("您没有此应用的复制权限");
                }
            } else {
                //开发复制
                //校验源空间权限
                spacePermissionService.checkSpaceUserPermission(sourceConfig.getSpaceId());
            }

            // 创建新项目
            CustomPageConfigModel newConfigModel = new CustomPageConfigModel();
            newConfigModel.setName(sourceConfig.getName());
            newConfigModel.setDescription(sourceConfig.getDescription());
            newConfigModel.setIcon(sourceConfig.getIcon());
            newConfigModel.setCoverImg(sourceConfig.getCoverImg());
            newConfigModel.setCoverImgSourceType(sourceConfig.getCoverImgSourceType());
            newConfigModel.setNeedLogin(sourceConfig.getNeedLogin());
            newConfigModel.setProjectType(sourceConfig.getProjectType());
            newConfigModel.setProxyConfigs(sourceConfig.getProxyConfigs());
            newConfigModel.setPageArgConfigs(sourceConfig.getPageArgConfigs());
            newConfigModel.setExt(sourceConfig.getExt());
            newConfigModel.setSpaceId(targetSpaceId);

            ReqResult<CustomPageConfigModel> createResult = customPageConfigApplicationService.create(newConfigModel, userContext);
            if (!createResult.isSuccess()) {
                log.error("[copyProject] 复制项目失败, message={}", createResult.getMessage());
                return ReqResult.error("0001", createResult.getMessage());
            }
            CustomPageConfigModel targetConfig = createResult.getData();

            //数据源复制
            List<DataSourceDto> newCreateDataSources = customPageConfigApplicationService.copyProjectDataSources(sourceConfig, targetConfig, userContext);

            //项目工程复制
            ReqResult<Map<String, Object>> copyResult = customPageBuildApplicationService.copyProject(sourceConfig.getId(), targetConfig.getId(), userContext);

            if (copyResult.isSuccess()) {
                CustomPageCreateRes res = new CustomPageCreateRes();
                res.setProjectId(targetConfig.getId());
                res.setSpaceId(targetSpaceId);
                return ReqResult.success();
            }

            //删除项目
            var deleteResult = customPageConfigApplicationService.deleteProject(targetConfig.getId(), userContext);
            if (!deleteResult.isSuccess()) {
                log.warn("[copyProject] targetProjectId={},复制项目工程失败后,删除项目失败,message={}", targetConfig.getId(), deleteResult.getMessage());
            }
            //删除数据源
            if (newCreateDataSources != null && !newCreateDataSources.isEmpty()) {
                newCreateDataSources.forEach(dataSource -> {
                    String type = dataSource.getType();
                    Long id = dataSource.getId();
                    try {
                        if ("plugin".equals(type)) {
                            pluginApplicationService.delete(id);
                        } else if ("workflow".equals(type)) {
                            workflowApplicationService.delete(id);
                        }
                    } catch (Exception e) {
                        log.error("[copyProject] 复制项目失败，projectId={}, targetSpaceId={},删除组件失败: type={}, id={}",
                                req.getProjectId(), req.getTargetSpaceId(), type, id, e);
                    }
                });
            }

            return ReqResult.error(copyResult.getCode(), copyResult.getMessage());
        } catch (SpacePermissionException e) {
            log.error("[copyProject] 复制项目失败，projectId={}, targetSpaceId={}, {}",
                    req.getProjectId(), req.getTargetSpaceId(), e.getMessage());
            return ReqResult.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("[copyProject] 复制项目失败，projectId={}, targetSpaceId={}",
                    req.getProjectId(), req.getTargetSpaceId(), e);
            return ReqResult.error("0001", e.getMessage());
        }
    }

    @RequireResource(PAGE_APP_CREATE)
    @Operation(summary = "创建反向代理项目", description = "创建反向代理项目")
    @PostMapping(value = "/create-reverse-proxy", produces = MediaType.APPLICATION_JSON_VALUE)
    public ReqResult<CustomPageCreateRes> createReverseProxy(@RequestBody CustomPageCreateReq req) {
        log.info("[Web] 接收到创建反向代理项目请求，projectName={}", req.getProjectName());
        try {
            UserContext userContext = getUser();
            CustomPageConfigModel model = new CustomPageConfigModel();
            model.setName(req.getProjectName());
            model.setDescription(req.getProjectDesc());
            model.setSpaceId(req.getSpaceId());
            model.setIcon(req.getIcon());
            model.setProjectType(ProjectType.REVERSE_PROXY);

            var result = customPageConfigApplicationService.createReverseProxyProject(model, userContext);
            if (!result.isSuccess()) {
                log.warn("[Web] 创建反向代理项目失败,message={}", result.getMessage());
                return ReqResult.create(result.getCode(), result.getMessage(), null);
            }

            CustomPageCreateRes res = CustomPageCreateRes.builder().projectId(result.getData().getId()).build();
            return ReqResult.success(res);
        } catch (SpacePermissionException e) {
            log.error("[Web] 创建反向代理项目失败，projectName={}, {}", req.getProjectName(), e.getMessage());
            return ReqResult.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("[Web] 创建反向代理项目失败，projectName={}", req.getProjectName(), e);
            return ReqResult.error("0001", e.getMessage());
        }
    }

    @RequireResource(PAGE_APP_QUERY_LIST)
    @Operation(summary = "查询项目列表", description = "查询项目列表")
    @GetMapping(value = "/list-projects", produces = MediaType.APPLICATION_JSON_VALUE)
    public ReqResult<List<CustomPageDto>> listProjects(CustomPageQueryReq req) {
        log.info("[Web]  接收到查询项目列表请求，req={}", req);
        try {
            List<CustomPageDto> listData = iCustomPageRpcService.list(req);
            return ReqResult.success(listData);
        } catch (Exception e) {
            log.error("[Web] 查询项目列表失败", e);
            return ReqResult.error("0001", e.getMessage());
        }
    }

    @RequireResource(PAGE_APP_QUERY_LIST)
    @Operation(summary = "分页查询项目", description = "分页查询项目")
    @GetMapping(value = "/page-query-projects", produces = MediaType.APPLICATION_JSON_VALUE)
    public ReqResult<SuperPage<CustomPageDto>> pageQueryProjects(
            @RequestBody PageQueryVo<CustomPageQueryReq> pageQueryVo) {
        log.info("[Web]  接收到分页查询项目请求，pageQueryVo={}", pageQueryVo);
        try {
            CustomPageQueryReq req = pageQueryVo.getQueryFilter();
            if (req == null) {
                throw new IllegalArgumentException("参数为空");
            }
            if (req.getSpaceId() == null) {
                throw new IllegalArgumentException("spaceId为必填参数");
            }
            // 校验空间权限
            spacePermissionService.checkSpaceUserPermission(req.getSpaceId());
            SuperPage<CustomPageDto> page = iCustomPageRpcService.pageQuery(pageQueryVo);
            return ReqResult.success(page);
        } catch (Exception e) {
            log.error("[Web] 分页查询项目失败", e);
            return ReqResult.error("0001", e.getMessage());
        }
    }

    @RequireResource(PAGE_APP_QUERY_DETAIL)
    @Operation(summary = "查询项目详情", description = "根据项目ID查询项目详情信息")
    @GetMapping(value = "/get-project-info", produces = MediaType.APPLICATION_JSON_VALUE)
    public ReqResult<CustomPageDto> getProjectInfo(@RequestParam("projectId") Long projectId) {
        log.info("[Web] 接收到查询项目详情请求，projectId={}", projectId);
        try {
            if (projectId == null || projectId <= 0) {
                return ReqResult.error("0001", "项目ID不能为空或无效");
            }

            CustomPageDto dto = iCustomPageRpcService.queryDetailWithVersion(projectId);
            if (dto == null) {
                return ReqResult.error("0002", "项目不存在");
            }

            return ReqResult.success(dto);
        } catch (SpacePermissionException e) {
            log.error("[Web] 查询项目详情，projectId={}, {}", projectId, e.getMessage());
            return ReqResult.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("[Web] 查询项目详情异常，projectId={}", projectId, e);
            return ReqResult.error("0001", e.getMessage());
        }
    }

    @RequireResource(PAGE_APP_QUERY_DETAIL)
    @Operation(summary = "查询项目详情(根据agentId)", description = "根据agentId查询项目详情信息")
    @GetMapping(value = "/get-project-info-by-agent", produces = MediaType.APPLICATION_JSON_VALUE)
    public ReqResult<CustomPageDto> getProjectInfoByAgentId(@RequestParam("agentId") Long agentId) {
        log.info("[Web] 接收到查询项目详情请求，agentId={}", agentId);
        try {
            if (agentId == null || agentId <= 0) {
                return ReqResult.error("0001", "agentId不能为空或无效");
            }

            CustomPageDto dto = iCustomPageRpcService.queryDetailByAgentId(agentId);
            if (dto == null) {
                return ReqResult.error("0002", "项目不存在");
            }

            return ReqResult.success(dto);
        } catch (SpacePermissionException e) {
            log.error("[Web] 查询项目详情，agentId={}, {}", agentId, e.getMessage());
            return ReqResult.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("[Web] 查询项目详情异常，agentId={}", agentId, e);
            return ReqResult.error("0001", e.getMessage());
        }
    }

    @RequireResource(PAGE_APP_QUERY_DETAIL)
    @Operation(summary = "查询项目文件内容", description = "查询项目文件内容")
    @GetMapping(value = "/get-project-content", produces = MediaType.APPLICATION_JSON_VALUE)
    public ReqResult<ProjectContentRes> getProjectContent(@RequestParam("projectId") Long projectId) {
        log.info("[Web] 接收到查询项目文件内容请求，projectId={}", projectId);
        try {
            String proxyPath = "/page/static/" + projectId;
            var result = customPageConfigApplicationService.queryProjectContent(projectId, proxyPath);
            if (!result.isSuccess()) {
                log.warn("[Web] projectId={},查询项目文件内容失败,message={}", projectId, result.getMessage());
                return ReqResult.create(result.getCode(), result.getMessage(), null);
            }

            Map<String, Object> data = result.getData();
            ProjectContentRes res = new ProjectContentRes();
            res.setFiles(data.get("files"));
            if (data.containsKey("frontendFramework")) {
                String frontendFramework = (String) data.get("frontendFramework");
                res.setFrontendFramework(frontendFramework);
            }
            if (data.containsKey("devFramework")) {
                String devFramework = (String) data.get("devFramework");
                res.setDevFramework(devFramework);
            }

            return ReqResult.success(res);
        } catch (SpacePermissionException e) {
            log.error("[Web] 查询项目文件内容失败，projectId={}, {}", projectId, e.getMessage());
            return ReqResult.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("[Web] 查询项目文件内容失败，projectId={}", projectId, e);
            return ReqResult.error("0001", e.getMessage());
        }
    }

    @RequireResource(PAGE_APP_QUERY_DETAIL)
    @Operation(summary = "查询项目历史版本内容", description = "查询项目历史版本内容")
    @GetMapping(value = "/get-project-content-by-version", produces = MediaType.APPLICATION_JSON_VALUE)
    public ReqResult<ProjectContentRes> getProjectContentByVersion(@RequestParam("projectId") Long projectId,
                                                                   @RequestParam("codeVersion") Integer codeVersion) {
        log.info("[Web] 接收到查询项目历史版本内容请求，projectId={}, codeVersion={}", projectId, codeVersion);
        try {
            if (projectId == null || projectId <= 0) {
                return ReqResult.error("0001", "项目ID不能为空或无效");
            }
            if (codeVersion == null || codeVersion < 0) {
                return ReqResult.error("0001", "版本号不能为空或无效");
            }
            String proxyPath = "/page/static/_his/" + projectId;
            var result = customPageConfigApplicationService.queryProjectContentByVersion(projectId, codeVersion, proxyPath);
            if (!result.isSuccess()) {
                log.warn("[Web] projectId={},查询项目历史版本内容失败,message={}", projectId, result.getMessage());
                return ReqResult.create(result.getCode(), result.getMessage(), null);
            }

            ProjectContentRes res = new ProjectContentRes();
            res.setFiles(result.getData().get("files"));

            return ReqResult.success(res);
        } catch (SpacePermissionException e) {
            log.error("[Web] 查询项目历史版本内容失败，projectId={}, codeVersion={}, {}", projectId, codeVersion, e.getMessage());
            return ReqResult.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("[Web] 查询项目历史版本内容失败，projectId={}, codeVersion={}", projectId, codeVersion, e);
            return ReqResult.error("0001", e.getMessage());
        }
    }

    @RequireResource(PAGE_APP_EXPORT)
    @Operation(summary = "导出用户前端项目", description = "导出项目为zip文件")
    @GetMapping(value = "/export-project")
    public ResponseEntity<byte[]> exportProject(@RequestParam("projectId") Long projectId) {
        log.info("[Web] 接收到导出项目请求，projectId={}", projectId);
        try {
            if (projectId == null || projectId <= 0) {
                return ResponseEntity.badRequest().build();
            }

            UserContext userContext = getUser();
            var result = customPageConfigApplicationService.exportProjectLatest(projectId, userContext);
            if (!result.isSuccess()) {
                log.warn("[Web] projectId={},导出项目失败,message={}", projectId, result.getMessage());
                return ResponseEntity.badRequest().build();
            }

            InputStream inputStream = result.getData();
            if (inputStream == null) {
                return ResponseEntity.notFound().build();
            }

            // 读取InputStream为byte数组
            byte[] bytes = inputStream.readAllBytes();
            inputStream.close();

            // 设置响应头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", "project_" + projectId + ".zip");
            headers.setContentLength(bytes.length);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(bytes);

        } catch (IOException e) {
            log.error("[Web] 导出项目异常，projectId={}", projectId, e);
            return ResponseEntity.internalServerError().build();
        } catch (SpacePermissionException e) {
            log.error("[Web] 导出项目失败，projectId={}, {}", projectId, e.getMessage());
            return ResponseEntity.internalServerError().build();
        } catch (Exception e) {
            log.error("[Web] 导出项目异常，projectId={}", projectId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @RequireResource(PAGE_APP_CONFIG_PROXY)
    @Operation(summary = "配置反向代理", description = "配置反向代理，会完全替换现有的代理配置")
    @PostMapping(value = "/batch-config-proxy", produces = MediaType.APPLICATION_JSON_VALUE)
    public ReqResult<Void> batchConfigProxy(@RequestBody ProxyConfigBatchReq req) {
        log.info("[Web] 接收到批量配置反向代理请求，projectId={}, configCount={}",
                req.getProjectId(), req.getProxyConfigs() != null ? req.getProxyConfigs().size() : 0);
        try {
            UserContext userContext = getUser();

            // 转换对象
            List<ProxyConfig> proxyConfigs = req.getProxyConfigs().stream()
                    .map(item -> ProxyConfig.builder()
                            .env(ProxyConfig.ProxyEnv.get(item.getEnv()))
                            .path(item.getPath())
                            .healthCheckPath(item.getHealthCheckPath())
                            .requireAuth(item.getRequireAuth() != null ? item.getRequireAuth() : true)
                            .backends(item.getBackends().stream()
                                    .map(backendReq -> new ProxyConfigBackend(backendReq.getBackend(),
                                            backendReq.getWeight() != null ? backendReq.getWeight() : 1))
                                    .collect(Collectors.toList()))
                            .build())
                    .collect(Collectors.toList());

            var result = customPageConfigApplicationService.batchConfigProxy(req.getProjectId(), proxyConfigs,
                    userContext);
            if (!result.isSuccess()) {
                log.warn("[Web] projectId={},批量配置反向代理失败,message={}", req.getProjectId(), result.getMessage());
                return ReqResult.create(result.getCode(), result.getMessage(), null);
            }

            return ReqResult.success();
        } catch (SpacePermissionException e) {
            log.error("[Web] 批量配置反向代理失败，projectId={}, {}", req.getProjectId(), e.getMessage());
            return ReqResult.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("[Web] 批量配置反向代理失败，projectId={}", req.getProjectId(), e);
            return ReqResult.error("0001", e.getMessage());
        }
    }

    @RequireResource(PAGE_APP_CONFIG_PATH)
    @Operation(summary = "保存路径参数", description = "保存路径参数")
    @PostMapping(value = "/save-path-args", produces = MediaType.APPLICATION_JSON_VALUE)
    public ReqResult<Void> savePathArgs(@RequestBody PageArgConfigReq req) {
        log.info("[Web] 接收到保存路径参数请求，projectId={}, pageUri={}", req.getProjectId(), req.getPageUri());
        try {
            UserContext userContext = getUser();

            PageArgConfig pageArgConfig = new PageArgConfig();
            pageArgConfig.setPageUri(req.getPageUri());
            pageArgConfig.setName(req.getName());
            pageArgConfig.setDescription(req.getDescription());

            if (req.getArgs() != null) {
                pageArgConfig.setArgs(req.getArgs().stream()
                        .map(argReq -> {
                            PageArg pageArg = new PageArg();
                            pageArg.setKey(argReq.getKey());
                            pageArg.setName(argReq.getName());
                            pageArg.setDescription(argReq.getDescription());
                            if (argReq.getDataType() != null) {
                                try {
                                    pageArg.setDataType(DataTypeEnum.valueOf(argReq.getDataType()));
                                } catch (IllegalArgumentException e) {
                                    log.warn("无效的数据类型: {}", argReq.getDataType());
                                    throw new BizException("无效的数据类型: " + argReq.getDataType());
                                }
                            }
                            pageArg.setRequire(argReq.getRequire() != null ? argReq.getRequire() : false);
                            pageArg.setEnable(argReq.getEnable() != null ? argReq.getEnable() : true);
                            pageArg.setBindValue(argReq.getBindValue());
                            if (argReq.getInputType() != null) {
                                try {
                                    pageArg.setInputType(InputTypeEnum.valueOf(argReq.getInputType()));
                                } catch (IllegalArgumentException e) {
                                    log.warn("无效的输入类型: {}", argReq.getInputType());
                                    throw new BizException("无效的输入类型: " + argReq.getInputType());
                                }
                            }
                            return pageArg;
                        })
                        .collect(Collectors.toList()));
            }

            var result = customPageConfigApplicationService.savePathArgs(req.getProjectId(), pageArgConfig,
                    userContext);
            if (!result.isSuccess()) {
                log.warn("[Web] projectId={},保存路径参数失败,message={}", req.getProjectId(), result.getMessage());
                return ReqResult.create(result.getCode(), result.getMessage(), null);
            }

            return ReqResult.success();
        } catch (SpacePermissionException e) {
            log.error("[Web] 保存路径参数失败，projectId={}, pageUri={}, {}", req.getProjectId(), req.getPageUri(),
                    e.getMessage());
            return ReqResult.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("[Web] 保存路径参数失败，projectId={}, pageUri={}", req.getProjectId(), req.getPageUri(), e);
            return ReqResult.error("0001", e.getMessage());
        }
    }

    @RequireResource(PAGE_APP_CONFIG_PATH)
    @Operation(summary = "添加路径配置", description = "添加路径配置，如果pageUri已存在则报错")
    @PostMapping(value = "/add-path", produces = MediaType.APPLICATION_JSON_VALUE)
    public ReqResult<Void> addPath(@RequestBody PageArgConfigReq req) {
        log.info("[Web] 接收到添加路径配置请求，projectId={}, pageUri={}", req.getProjectId(), req.getPageUri());
        try {
            UserContext userContext = getUser();

            PageArgConfig pageArgConfig = new PageArgConfig();
            pageArgConfig.setPageUri(req.getPageUri());
            pageArgConfig.setName(req.getName());
            pageArgConfig.setDescription(req.getDescription());
            pageArgConfig.setArgs(new ArrayList<>());

            var result = customPageConfigApplicationService.addPath(req.getProjectId(), pageArgConfig,
                    userContext);
            if (!result.isSuccess()) {
                log.warn("[Web] projectId={},添加路径配置失败,message={}", req.getProjectId(), result.getMessage());
                return ReqResult.create(result.getCode(), result.getMessage(), null);
            }

            return ReqResult.success();
        } catch (SpacePermissionException e) {
            log.error("[Web] 添加路径配置失败，projectId={}, pageUri={}, {}", req.getProjectId(), req.getPageUri(),
                    e.getMessage());
            return ReqResult.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("[Web] 添加路径配置失败，projectId={}, pageUri={}", req.getProjectId(), req.getPageUri(), e);
            return ReqResult.error("0001", e.getMessage());
        }
    }

    @RequireResource(PAGE_APP_CONFIG_PATH)
    @Operation(summary = "编辑路径配置", description = "编辑路径配置，如果路径不存在则报错")
    @PostMapping(value = "/edit-path", produces = MediaType.APPLICATION_JSON_VALUE)
    public ReqResult<Void> editPath(@RequestBody PageArgConfigReq req) {
        log.info("[Web] 接收到编辑路径配置请求，projectId={}, pageUri={}", req.getProjectId(), req.getPageUri());
        try {
            UserContext userContext = getUser();

            PageArgConfig pageArgConfig = new PageArgConfig();
            pageArgConfig.setPageUri(req.getPageUri());
            pageArgConfig.setName(req.getName());
            pageArgConfig.setDescription(req.getDescription());

            var result = customPageConfigApplicationService.editPath(req.getProjectId(), pageArgConfig,
                    userContext);
            if (!result.isSuccess()) {
                log.warn("[Web] projectId={},编辑路径配置失败,message={}", req.getProjectId(), result.getMessage());
                return ReqResult.create(result.getCode(), result.getMessage(), null);
            }

            return ReqResult.success();
        } catch (SpacePermissionException e) {
            log.error("[Web] 编辑路径配置失败，projectId={}, pageUri={}, {}", req.getProjectId(), req.getPageUri(),
                    e.getMessage());
            return ReqResult.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("[Web] 编辑路径配置异常，projectId={}, pageUri={}", req.getProjectId(), req.getPageUri(), e);
            return ReqResult.error("0001", e.getMessage());
        }
    }

    @RequireResource(PAGE_APP_CONFIG_PATH)
    @Operation(summary = "删除路径配置", description = "删除路径配置，如果路径不存在则报错")
    @PostMapping(value = "/delete-path", produces = MediaType.APPLICATION_JSON_VALUE)
    public ReqResult<Void> deletePath(@RequestBody DeletePathReq req) {
        log.info("[Web] 接收到删除路径配置请求，projectId={}, pageUri={}", req.getProjectId(), req.getPageUri());
        try {
            UserContext userContext = getUser();

            var result = customPageConfigApplicationService.deletePath(req.getProjectId(), req.getPageUri(),
                    userContext);
            if (!result.isSuccess()) {
                log.warn("[Web] projectId={},删除路径配置失败,message={}", req.getProjectId(), result.getMessage());
                return ReqResult.create(result.getCode(), result.getMessage(), null);
            }

            return ReqResult.success();
        } catch (SpacePermissionException e) {
            log.error("[Web] 删除路径配置失败，projectId={}, pageUri={}, {}", req.getProjectId(), req.getPageUri(),
                    e.getMessage());
            return ReqResult.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("[Web] 删除路径配置失败，projectId={}, pageUri={}", req.getProjectId(), req.getPageUri(), e);
            return ReqResult.error("0001", e.getMessage());
        }
    }

    @RequireResource(PAGE_APP_MODIFY)
    @Operation(summary = "绑定数据源", description = "绑定数据源，支持plugin和workflow类型")
    @PostMapping(value = "/bind-data-source", produces = MediaType.APPLICATION_JSON_VALUE)
    public ReqResult<Void> bindDataSource(@RequestBody BindDataSourceReq req) {
        log.info("[Web] 接收到绑定数据源请求，projectId={}, type={}, dataSourceId={}",
                req.getProjectId(), req.getType(), req.getDataSourceId());
        try {
            UserContext userContext = getUser();

            var result = customPageConfigApplicationService.bindDataSource(
                    req.getProjectId(),
                    req.getType(),
                    req.getDataSourceId(),
                    userContext);
            if (!result.isSuccess()) {
                log.warn("[Web] projectId={},绑定数据源失败,message={}", req.getProjectId(), result.getMessage());
                return ReqResult.create(result.getCode(), result.getMessage(), null);
            }

            return ReqResult.success();
        } catch (SpacePermissionException e) {
            log.error("[Web] 绑定数据源失败，projectId={}, type={}, dataSourceId={}, {}", req.getProjectId(), req.getType(),
                    req.getDataSourceId(), e.getMessage());
            return ReqResult.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("[Web] 绑定数据源失败，projectId={}, type={}, dataSourceId={}",
                    req.getProjectId(), req.getType(), req.getDataSourceId(), e);
            return ReqResult.error("0001", e.getMessage());
        }
    }

    @RequireResource(PAGE_APP_MODIFY)
    @Operation(summary = "解绑数据源", description = "解绑数据源")
    @PostMapping(value = "/unbind-data-source", produces = MediaType.APPLICATION_JSON_VALUE)
    public ReqResult<Void> unbindDataSource(@RequestBody BindDataSourceReq req) {
        log.info("[Web] 接收到解绑数据源请求，projectId={}, type={}, dataSourceId={}",
                req.getProjectId(), req.getType(), req.getDataSourceId());
        try {
            UserContext userContext = getUser();

            var result = customPageConfigApplicationService.unbindDataSource(
                    req.getProjectId(),
                    req.getType(),
                    req.getDataSourceId(),
                    userContext);
            if (!result.isSuccess()) {
                log.warn("[Web] projectId={},解绑数据源失败,message={}", req.getProjectId(), result.getMessage());
                return ReqResult.create(result.getCode(), result.getMessage(), null);
            }

            return ReqResult.success();
        } catch (SpacePermissionException e) {
            log.error("[Web] 解绑数据源失败，projectId={}, type={}, dataSourceId={}, {}", req.getProjectId(), req.getType(),
                    req.getDataSourceId(), e.getMessage());
            return ReqResult.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("[Web] 解绑数据源失败，projectId={}, type={}, dataSourceId={}",
                    req.getProjectId(), req.getType(), req.getDataSourceId(), e);
            return ReqResult.error("0001", e.getMessage());
        }
    }

}
