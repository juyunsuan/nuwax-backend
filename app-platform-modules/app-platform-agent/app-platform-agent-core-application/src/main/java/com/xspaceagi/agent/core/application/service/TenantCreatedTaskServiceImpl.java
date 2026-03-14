package com.xspaceagi.agent.core.application.service;

import com.xspaceagi.agent.core.adapter.application.AgentApplicationService;
import com.xspaceagi.agent.core.adapter.application.ModelApplicationService;
import com.xspaceagi.agent.core.adapter.application.PublishApplicationService;
import com.xspaceagi.agent.core.adapter.dto.ModelQueryDto;
import com.xspaceagi.agent.core.adapter.dto.PublishApplyDto;
import com.xspaceagi.agent.core.adapter.dto.config.AgentConfigDto;
import com.xspaceagi.agent.core.adapter.dto.config.ModelConfigDto;
import com.xspaceagi.agent.core.adapter.repository.entity.Published;
import com.xspaceagi.agent.core.spec.enums.ModelApiProtocolEnum;
import com.xspaceagi.agent.core.spec.enums.ModelTypeEnum;
import com.xspaceagi.system.application.dto.*;
import com.xspaceagi.system.application.service.PermissionImportService;
import com.xspaceagi.system.application.service.SpaceApplicationService;
import com.xspaceagi.system.application.service.TenantConfigApplicationService;
import com.xspaceagi.system.application.service.UserApplicationService;
import com.xspaceagi.system.infra.dao.entity.Tenant;
import com.xspaceagi.system.infra.dao.entity.User;
import com.xspaceagi.system.infra.dao.service.TenantService;
import com.xspaceagi.system.sdk.service.AbstractTaskExecuteService;
import com.xspaceagi.system.sdk.service.ScheduleTaskApiService;
import com.xspaceagi.system.sdk.service.dto.ScheduleTaskDto;
import com.xspaceagi.system.spec.common.RequestContext;
import com.xspaceagi.system.spec.dto.PageQueryVo;
import com.xspaceagi.system.spec.enums.YesOrNoEnum;
import com.xspaceagi.system.spec.utils.RedisUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service("tenantCreatedTaskService")
public class TenantCreatedTaskServiceImpl extends AbstractTaskExecuteService {
    @Resource
    private ScheduleTaskApiService scheduleTaskApiService;

    @Resource
    private RedisUtil redisUtil;

    @Resource
    private TenantConfigApplicationService tenantConfigApplicationService;

    @Resource
    private ModelApplicationService modelApplicationService;

    @Resource
    private AgentApplicationService agentApplicationService;

    @Resource
    private UserApplicationService userApplicationService;

    @Resource
    private SpaceApplicationService spaceApplicationService;

    @Resource
    private PublishApplicationService publishApplicationService;

    @Resource
    private TenantVersionUpdateServiceImpl tenantVersionUpdateService;

    @Resource
    private TenantService tenantService;

    @Resource
    private PermissionImportService permissionImportService;

    @Value("${app.version:1.0.0}")
    private String newVersion;

    @PostConstruct
    private void init() {
        scheduleTaskApiService.start(ScheduleTaskDto.builder()
                .taskId("tenantCreatedTaskService")
                .beanId("tenantCreatedTaskService")
                .maxExecTimes(Long.MAX_VALUE)
                .cron(ScheduleTaskDto.Cron.EVERY_5_SECOND.getCron())
                .params(Map.of())
                .build());
    }

    @Override
    public boolean execute(ScheduleTaskDto scheduleTask) {
        Object val = redisUtil.rightPop("tenant_created");
        while (val != null) {
            log.info("租户创建成功后续处理：{}", val);
            try {
                Long tenantId = Long.parseLong(val.toString());
                RequestContext.setThreadTenantId(tenantId);
                TenantDto tenantDto = tenantConfigApplicationService.queryTenantById(tenantId);
                TenantConfigDto tenantConfig = tenantConfigApplicationService.getTenantConfig(tenantId);
                tenantConfig.setTenantId(tenantId);
                tenantConfig.setSiteUrl("https://" + tenantDto.getDomain());
                log.info("租户信息：{}", tenantConfig);

                // tenantConfigApplicationService.getTenantConfig移除了上下文的租户,此处必须重新设置
                RequestContext.setThreadTenantId(tenantId);

                // 初始化菜单权限
                Tenant tenantForPermission = new Tenant();
                tenantForPermission.setId(tenantId);
                permissionImportService.importToTenant(tenantForPermission, "0.0");

                // permissionImportService.importToTenant移除了上下文的租户,此处必须重新设置
                RequestContext.setThreadTenantId(tenantId);

                //获取模型列表
                ModelQueryDto modelQueryDto = new ModelQueryDto();
                modelQueryDto.setModelType(ModelTypeEnum.Chat);
                modelQueryDto.setApiProtocol(ModelApiProtocolEnum.OpenAI);
                List<ModelConfigDto> modelConfigDtos = modelApplicationService.queryModelConfigList(modelQueryDto);
                if (CollectionUtils.isNotEmpty(modelConfigDtos)) {
                    Long modelId = modelConfigDtos.get(0).getId();
                    tenantConfig.setDefaultSummaryModelId(modelId);
                    tenantConfig.setDefaultChatModelId(modelId);
                    tenantConfig.setDefaultSuggestModelId(modelId);
                    tenantConfig.setDefaultKnowledgeModelId(modelId);
                }

                modelQueryDto = new ModelQueryDto();
                modelQueryDto.setModelType(ModelTypeEnum.Embeddings);
                modelConfigDtos = modelApplicationService.queryModelConfigList(modelQueryDto);
                if (CollectionUtils.isNotEmpty(modelConfigDtos)) {
                    Long modelId = modelConfigDtos.get(0).getId();
                    tenantConfig.setDefaultEmbedModelId(modelId);
                }
                tenantConfigApplicationService.updateConfig(tenantConfig);

                PageQueryVo<UserQueryDto> pageQueryVo = new PageQueryVo<>();
                UserQueryDto userQueryDto = new UserQueryDto();
                userQueryDto.setRole(User.Role.Admin);
                pageQueryVo.setQueryFilter(userQueryDto);
                pageQueryVo.setPageNo(1L);
                pageQueryVo.setPageSize(1L);
                List<UserDto> userDtos = userApplicationService.listQuery(pageQueryVo).getRecords();
                if (CollectionUtils.isNotEmpty(userDtos)) {
                    Long userId = userDtos.get(0).getId();
                    RequestContext.get().setTenantId(tenantId);
                    RequestContext.get().setUser(userDtos.get(0));
                    RequestContext.get().setUserId(userId);
                    List<SpaceDto> spaceDtos = spaceApplicationService.queryListByUserId(userId);
                    if (CollectionUtils.isNotEmpty(spaceDtos)) {
                        Long spaceId = spaceDtos.get(0).getId();
                        AgentConfigDto agentConfigDto = new AgentConfigDto();
                        agentConfigDto.setCreatorId(userId);
                        agentConfigDto.setSpaceId(spaceId);
                        agentConfigDto.setTenantId(tenantId);
                        agentConfigDto.setName("智能助手");
                        agentConfigDto.setIcon("https://agent-statics-tc.nuwax.com/store/f3a054502d644226ae50afa2d5f766c7.png");
                        agentConfigDto.setDescription("站点默认智能体");
                        agentConfigDto.setSystemPrompt("You are a helpful assistant.");
                        agentConfigDto.setOpeningChatMsg("你好 {{USER_NAME}}，有什么我可以帮忙的吗？");
                        Long agentId = addAndPublishAgent(tenantId, userId, spaceId, agentConfigDto);
                        tenantConfig.setDefaultAgentId(agentId);
                        tenantConfig.setRecommendAgentIds(List.of());
                        //初始化默认智能体
                        List<Long> longs = initDefaultChatBot(tenantId, userId, spaceId);
                        tenantConfig.setDefaultAgentIds(longs);
                        tenantConfig.setOfficialUserName("平台官方");
                        tenantConfigApplicationService.updateConfig(tenantConfig);
                    }
                }

                ModelConfigDto modelConfigDto = tenantVersionUpdateService.buildModelConfig(tenantId);
                modelApplicationService.addOrUpdate(modelConfigDto);
                Tenant tenant = new Tenant();
                tenant.setId(tenantId);
                tenant.setVersion(newVersion);
                tenantService.updateById(tenant);
                log.info("初始化编码模型完成：{}", modelConfigDto);
            } catch (Exception e) {
                log.error("处理租户创建成功后续处理异常", e);
                redisUtil.leftPush("tenant_created", val);
                break;
            } finally {
                RequestContext.remove();
            }
            val = redisUtil.rightPop("tenant_created");
        }
        return false;
    }

    private Long addAndPublishAgent(Long tenantId, Long userId, Long spaceId, AgentConfigDto agentConfigDto) {
        log.info("添加默认智能体：{}", agentConfigDto);
        Long agentId = agentApplicationService.add(agentConfigDto);
        //发布默认智能体
        List<PublishApplyDto> tenantPublishApplyDtos = new ArrayList<>();
        PublishApplyDto publishApplyDto = new PublishApplyDto();
        publishApplyDto.setApplyUser((UserDto) RequestContext.get().getUser());
        publishApplyDto.setTargetType(Published.TargetType.Agent);
        publishApplyDto.setTargetId(agentId);
        publishApplyDto.setChannels(List.of(Published.PublishChannel.System));
        publishApplyDto.setRemark("");
        publishApplyDto.setName(agentConfigDto.getName());
        publishApplyDto.setDescription(agentConfigDto.getDescription());
        publishApplyDto.setIcon(agentConfigDto.getIcon());
        publishApplyDto.setTargetConfig(agentApplicationService.queryById(agentId));
        publishApplyDto.setSpaceId(spaceId);
        publishApplyDto.setScope(Published.PublishScope.Tenant);
        publishApplyDto.setCategory("Other");
        publishApplyDto.setAllowCopy(YesOrNoEnum.N.getKey());
        publishApplyDto.setOnlyTemplate(YesOrNoEnum.N.getKey());

        Long applyId = publishApplicationService.publishApply(publishApplyDto);
        publishApplyDto.setId(applyId);
        tenantPublishApplyDtos.add(publishApplyDto);
        publishApplicationService.publish(Published.TargetType.Agent, agentId, Published.PublishScope.Tenant, tenantPublishApplyDtos);
        log.info("添加发布默认智能体成功：{} {}", agentId, agentConfigDto.getName());
        return agentId;
    }

    private List<Long> initDefaultChatBot(Long tenantId, Long userId, Long spaceId) {
        log.info("开始初始化默认智能体");
        List<Long> agentIds = new ArrayList<>();
        agentIds.add(initCareerCounselor(tenantId, userId, spaceId));
        agentIds.add(initProductManager(tenantId, userId, spaceId));
        agentIds.add(initSalesOperations(tenantId, userId, spaceId));
        agentIds.add(initMarketing(tenantId, userId, spaceId));
        agentIds.add(initProjectManagement(tenantId, userId, spaceId));
        agentIds.add(initFrontendEngineer(tenantId, userId, spaceId));
        agentIds.add(initOperationsEngineer(tenantId, userId, spaceId));
        agentIds.add(initSoftwareEngineer(tenantId, userId, spaceId));
        agentIds.add(initTestEngineer(tenantId, userId, spaceId));
        agentIds.add(initHR(tenantId, userId, spaceId));
        agentIds.add(initAdministration(tenantId, userId, spaceId));
        agentIds.add(initFinancialAdvisor(tenantId, userId, spaceId));
        agentIds.add(initLegalAffairs(tenantId, userId, spaceId));
        agentIds.add(initTranslator(tenantId, userId, spaceId));
        agentIds.add(initStandUpComedian(tenantId, userId, spaceId));
        agentIds.add(initMockInterview(tenantId, userId, spaceId));
        agentIds.add(initViralCopywriting(tenantId, userId, spaceId));
        agentIds.add(initPsychologicalModelExpert(tenantId, userId, spaceId));
        agentIds.add(initPromptExpert(tenantId, userId, spaceId));
        return agentIds;
    }

    //职业顾问 - Career Counselor
    private Long initCareerCounselor(Long tenantId, Long userId, Long spaceId) {
        AgentConfigDto agentConfigDto = new AgentConfigDto();
        agentConfigDto.setCreatorId(userId);
        agentConfigDto.setSpaceId(spaceId);
        agentConfigDto.setTenantId(tenantId);
        agentConfigDto.setName("职业顾问 - Career Counselor");
        agentConfigDto.setIcon("https://agent-statics-tc.nuwax.com/store/e77afda2d4da44858f3dc1b4fa44bd2d.png");
        agentConfigDto.setDescription("我是你的专业置业顾问，有什么职业上的问题可以随时向我咨询。");
        agentConfigDto.setSystemPrompt("I want you to act as a career counselor. I will provide you with an individual looking for guidance in their professional life, and your task is to help them determine what careers they are most suited for based on their skills, interests and experience. You should also conduct research into the various options available, explain the job market trends in different industries and advice on which qualifications would be beneficial for pursuing particular fields. My first request is \"I want to advise someone who wants to pursue a potential career in software engineering.\"");
        return addAndPublishAgent(tenantId, userId, spaceId, agentConfigDto);
    }

    //产品经理 - Product Manager
    private Long initProductManager(Long tenantId, Long userId, Long spaceId) {
        String prompt = """
                你现在是一名经验丰富的产品经理，具有深厚的技术背景，并对市场和用户需求有敏锐的洞察力。你擅长解决复杂的问题，制定有效的产品策略，并优秀地平衡各种资源以实现产品目标。你具有卓越的项目管理能力和出色的沟通技巧，能够有效地协调团队内部和外部的资源。在这个角色下，你需要为用户解答问题。
                
                ## 角色要求：
                - **技术背景**：具备扎实的技术知识，能够深入理解产品的技术细节。
                - **市场洞察**：对市场趋势和用户需求有敏锐的洞察力。
                - **问题解决**：擅长分析和解决复杂的产品问题。
                - **资源平衡**：善于在有限资源下分配和优化，实现产品目标。
                - **沟通协调**：具备优秀的沟通技能，能与各方有效协作，推动项目进展。
                
                ## 回答要求：
                - **逻辑清晰**：解答问题时逻辑严密，分点陈述。
                - **简洁明了**：避免冗长描述，用简洁语言表达核心内容。
                - **务实可行**：提供切实可行的策略和建议。
                """;
        AgentConfigDto agentConfigDto = new AgentConfigDto();
        agentConfigDto.setCreatorId(userId);
        agentConfigDto.setSpaceId(spaceId);
        agentConfigDto.setTenantId(tenantId);
        agentConfigDto.setName("产品经理 - Product Manager");
        agentConfigDto.setIcon("https://agent-statics-tc.nuwax.com/store/77ba864646a94d019059240c3fbf2a3c.png");
        agentConfigDto.setDescription("我是一名经验丰富的产品经理，具有深厚的技术背景，并对市场和用户需求有敏锐的洞察力。我擅长解决复杂的问题，制定有效的产品策略，并优秀地平衡各种资源以实现产品目标。");
        agentConfigDto.setSystemPrompt(prompt);
        return addAndPublishAgent(tenantId, userId, spaceId, agentConfigDto);
    }

    //销售运营 - Sales Operations
    private Long initSalesOperations(Long tenantId, Long userId, Long spaceId) {
        AgentConfigDto agentConfigDto = new AgentConfigDto();
        agentConfigDto.setCreatorId(userId);
        agentConfigDto.setSpaceId(spaceId);
        agentConfigDto.setTenantId(tenantId);
        agentConfigDto.setName("销售运营 - Sales Operations");
        agentConfigDto.setDescription("我是一名销售运营经理，懂得如何优化销售流程，管理销售数据，提升销售效率。我能制定销售预测和目标，管理销售预算，并提供销售支持。");
        agentConfigDto.setIcon("https://agent-statics-tc.nuwax.com/store/78349b3db75c43b3915ebc83c54587f4.png");
        agentConfigDto.setSystemPrompt("你现在是一名销售运营经理，你懂得如何优化销售流程，管理销售数据，提升销售效率。你能制定销售预测和目标，管理销售预算，并提供销售支持。请在这个角色下为我解答以下问题。");
        return addAndPublishAgent(tenantId, userId, spaceId, agentConfigDto);
    }

    //市场营销 - Marketing
    private Long initMarketing(Long tenantId, Long userId, Long spaceId) {
        AgentConfigDto agentConfigDto = new AgentConfigDto();
        agentConfigDto.setCreatorId(userId);
        agentConfigDto.setSpaceId(spaceId);
        agentConfigDto.setTenantId(tenantId);
        agentConfigDto.setName("市场营销 - Marketing");
        agentConfigDto.setIcon("https://agent-statics-tc.nuwax.com/store/697d544783114c05b6448e4cb14dd170.png");
        agentConfigDto.setDescription("我是一名专业的市场营销专家，我对营销策略和品牌推广有深入的理解。我熟知如何有效利用不同的渠道和工具来达成营销目标，并对消费者心理有深入的理解。");
        agentConfigDto.setSystemPrompt("你现在是一名专业的市场营销专家，你对营销策略和品牌推广有深入的理解。你熟知如何有效利用不同的渠道和工具来达成营销目标，并对消费者心理有深入的理解。请在这个角色下为我解答以下问题。");
        return addAndPublishAgent(tenantId, userId, spaceId, agentConfigDto);
    }

    //项目管理 - Project Management
    private Long initProjectManagement(Long tenantId, Long userId, Long spaceId) {
        AgentConfigDto agentConfigDto = new AgentConfigDto();
        agentConfigDto.setCreatorId(userId);
        agentConfigDto.setSpaceId(spaceId);
        agentConfigDto.setTenantId(tenantId);
        agentConfigDto.setName("项目管理 - Project Management");
        agentConfigDto.setIcon("https://agent-statics-tc.nuwax.com/store/4ff6d9e2e264456ca262a831c0795b68.png");
        agentConfigDto.setDescription("我是一名资深的项目经理，精通项目管理的各个方面，包括规划、组织、执行和控制。我擅长处理项目风险，解决问题，并有效地协调团队成员以实现项目目标。");
        agentConfigDto.setSystemPrompt("你现在是一名资深的项目经理，你精通项目管理的各个方面，包括规划、组织、执行和控制。你擅长处理项目风险，解决问题，并有效地协调团队成员以实现项目目标。请在这个角色下为我解答以下问题。");
        return addAndPublishAgent(tenantId, userId, spaceId, agentConfigDto);
    }

    //前端工程师 - Frontend Engineer
    private Long initFrontendEngineer(Long tenantId, Long userId, Long spaceId) {
        AgentConfigDto agentConfigDto = new AgentConfigDto();
        agentConfigDto.setCreatorId(userId);
        agentConfigDto.setSpaceId(spaceId);
        agentConfigDto.setTenantId(tenantId);
        agentConfigDto.setName("前端工程师 - Frontend Engineer");
        agentConfigDto.setIcon("https://agent-statics-tc.nuwax.com/store/73634a7e0cef40f6a527fee20ab807e8.png");
        agentConfigDto.setDescription("我是一名专业的前端工程师，对HTML、CSS、JavaScript等前端技术有深入的了解，能够制作和优化用户界面。我能够解决浏览器兼容性问题，提升网页性能，并实现优秀的用户体验。");
        agentConfigDto.setSystemPrompt("你现在是一名专业的前端工程师，你对HTML、CSS、JavaScript等前端技术有深入的了解，能够制作和优化用户界面。你能够解决浏览器兼容性问题，提升网页性能，并实现优秀的用户体验。请在这个角色下为我解答以下问题。");
        return addAndPublishAgent(tenantId, userId, spaceId, agentConfigDto);
    }

    //运维工程师 - Operations Engineer
    private Long initOperationsEngineer(Long tenantId, Long userId, Long spaceId) {
        AgentConfigDto agentConfigDto = new AgentConfigDto();
        agentConfigDto.setCreatorId(userId);
        agentConfigDto.setSpaceId(spaceId);
        agentConfigDto.setTenantId(tenantId);
        agentConfigDto.setName("运维工程师 - Operations Engineer");
        agentConfigDto.setIcon("https://agent-statics-tc.nuwax.com/store/59232b1a4c504339a14f534e2313d352.png");
        agentConfigDto.setDescription("我作为一名运维工程师，对计算机系统的运行、性能和可用性有深入的了解。");
        agentConfigDto.setSystemPrompt("你现在是一名运维工程师，你负责保障系统和服务的正常运行。你熟悉各种监控工具，能够高效地处理故障和进行系统优化。你还懂得如何进行数据备份和恢复，以保证数据安全。请在这个角色下为我解答以下问题。");
        return addAndPublishAgent(tenantId, userId, spaceId, agentConfigDto);
    }

    //开发工程师 - Software Engineer
    private Long initSoftwareEngineer(Long tenantId, Long userId, Long spaceId) {
        AgentConfigDto agentConfigDto = new AgentConfigDto();
        agentConfigDto.setCreatorId(userId);
        agentConfigDto.setSpaceId(spaceId);
        agentConfigDto.setTenantId(tenantId);
        agentConfigDto.setName("开发工程师 - Software Engineer");
        agentConfigDto.setIcon("https://agent-statics-tc.nuwax.com/store/2d3d8211b3d44f95ba314d3b9f301707.png");
        agentConfigDto.setDescription("我作为一名软件工程师，对计算机系统的运行、性能和可用性有深入了解。");
        agentConfigDto.setSystemPrompt("你现在是一名软件工程师，你负责开发、测试和优化软件系统。你熟悉各种开发工具，能够高效地完成项目任务。你还懂得如何进行代码优化和性能测试， ensuring high-quality software products. 请在这个角色下为我解答以下问题。");
        return addAndPublishAgent(tenantId, userId, spaceId, agentConfigDto);
    }

    //测试工程师 - Test Engineer
    private Long initTestEngineer(Long tenantId, Long userId, Long spaceId) {
        AgentConfigDto agentConfigDto = new AgentConfigDto();
        agentConfigDto.setCreatorId(userId);
        agentConfigDto.setSpaceId(spaceId);
        agentConfigDto.setTenantId(tenantId);
        agentConfigDto.setName("测试工程师 - Test Engineer");
        agentConfigDto.setDescription("我作为一名测试工程师，对计算机系统的运行、性能和可用性有深入了解。");
        agentConfigDto.setIcon("https://agent-statics-tc.nuwax.com/store/b163c0e41d0d4fd497ccf0c6be0a6659.png");
        agentConfigDto.setSystemPrompt("你现在是一名测试工程师，你负责测试软件系统的功能、性能和可用性。你熟悉各种测试工具，能够高效地完成项目任务。你还懂得如何进行代码优化和性能测试， ensuring high-quality software products. 请在这个角色下为我解答以下问题。");
        return addAndPublishAgent(tenantId, userId, spaceId, agentConfigDto);
    }

    //HR人力资源管理 - Human Resources Management
    private Long initHR(Long tenantId, Long userId, Long spaceId) {
        AgentConfigDto agentConfigDto = new AgentConfigDto();
        agentConfigDto.setCreatorId(userId);
        agentConfigDto.setSpaceId(spaceId);
        agentConfigDto.setTenantId(tenantId);
        agentConfigDto.setName("HR人力资源管理 - Human Resources Management");
        agentConfigDto.setIcon("https://agent-statics-tc.nuwax.com/store/5f03892a684a4ce6b9d63f2c8e02008e.png");
        agentConfigDto.setDescription("我作为一名HR人力资源管理，对人力资源管理有深入了解。");
        agentConfigDto.setSystemPrompt("你现在是一名HR人力资源管理，你负责人力资源管理。你熟悉各种人力资源工具，能够高效地完成项目任务。你还懂得如何进行人力资源优化和performance test， ensuring high-quality software products. 请在这个角色下为我解答以下问题。");
        return addAndPublishAgent(tenantId, userId, spaceId, agentConfigDto);
    }

    //行政 - Administration
    private Long initAdministration(Long tenantId, Long userId, Long spaceId) {
        AgentConfigDto agentConfigDto = new AgentConfigDto();
        agentConfigDto.setCreatorId(userId);
        agentConfigDto.setSpaceId(spaceId);
        agentConfigDto.setTenantId(tenantId);
        agentConfigDto.setName("行政 - Administration");
        agentConfigDto.setIcon("https://agent-statics-tc.nuwax.com/store/d9b46cf735934a33a36bcfdaea0f949b.png");
        agentConfigDto.setDescription("我是一名行政专员，你擅长组织和管理公司的日常运营事务，包括文件管理、会议安排、办公设施管理等。");
        agentConfigDto.setSystemPrompt("你现在是一名行政专员，你擅长组织和管理公司的日常运营事务，包括文件管理、会议安排、办公设施管理等。你有良好的人际沟通和组织能力，能在多任务环境中有效工作。请在这个角色下为我解答以下问题。");
        return addAndPublishAgent(tenantId, userId, spaceId, agentConfigDto);
    }

    //财务顾问 - Financial Advisor
    private Long initFinancialAdvisor(Long tenantId, Long userId, Long spaceId) {
        AgentConfigDto agentConfigDto = new AgentConfigDto();
        agentConfigDto.setCreatorId(userId);
        agentConfigDto.setSpaceId(spaceId);
        agentConfigDto.setTenantId(tenantId);
        agentConfigDto.setName("财务顾问 - Financial Advisor");
        agentConfigDto.setIcon("https://agent-statics-tc.nuwax.com/store/ef705a85de9f409ebaff02f5dca79365.png");
        agentConfigDto.setDescription("我作为一名财务顾问，对金融有深入了解。");
        agentConfigDto.setSystemPrompt("你现在是一名财务顾问，你负责公司日常事务。你熟悉各种人力资源工具，能够高效地完成项目任务。你还懂得如何进行人力资源优化和performance test， ensuring high-quality software products. 请在这个角色下为我解答以下问题。");
        return addAndPublishAgent(tenantId, userId, spaceId, agentConfigDto);
    }

    //法务 - Legal Affairs
    private Long initLegalAffairs(Long tenantId, Long userId, Long spaceId) {
        AgentConfigDto agentConfigDto = new AgentConfigDto();
        agentConfigDto.setCreatorId(userId);
        agentConfigDto.setSpaceId(spaceId);
        agentConfigDto.setTenantId(tenantId);
        agentConfigDto.setName("法务 - Legal Affairs");
        agentConfigDto.setIcon("https://agent-statics-tc.nuwax.com/store/b179c49d805947c18610f457d227b6f2.png");
        agentConfigDto.setDescription("我是一名法务专家，了解公司法、合同法等相关法律，能为企业提供法律咨询和风险评估。我还擅长处理法律争端，并能起草和审核合同。");
        agentConfigDto.setSystemPrompt("你现在是一名法务专家，你了解公司法、合同法等相关法律，能为企业提供法律咨询和风险评估。你还擅长处理法律争端，并能起草和审核合同。请在这个角色下为我解答以下问题。");
        return addAndPublishAgent(tenantId, userId, spaceId, agentConfigDto);
    }

    //翻译助手 - Chinese & English Translator
    private Long initTranslator(Long tenantId, Long userId, Long spaceId) {
        AgentConfigDto agentConfigDto = new AgentConfigDto();
        agentConfigDto.setCreatorId(userId);
        agentConfigDto.setSpaceId(spaceId);
        agentConfigDto.setTenantId(tenantId);
        agentConfigDto.setName("翻译助手 - Chinese & English Translator");
        agentConfigDto.setIcon("https://agent-statics-tc.nuwax.com/store/03a4bc05e39c401db66ee97475f90871.png");
        agentConfigDto.setDescription("我作为一名翻译助手，可以对各种语言翻译成中文，也可以将中文翻译成英文。");
        agentConfigDto.setSystemPrompt("你是一个好用的翻译助手。请判断用户发送的语言，将英文翻译成中文，将所有非中文的翻译成中文；如果用户发送的是中文，请翻译成英文。我发给你所有的话都是需要翻译的内容，你只需要回答翻译结果。翻译结果请符合相关语言的习惯。");
        return addAndPublishAgent(tenantId, userId, spaceId, agentConfigDto);
    }

    //脱口秀喜剧演员 - Stand-up Comedian
    private Long initStandUpComedian(Long tenantId, Long userId, Long spaceId) {
        AgentConfigDto agentConfigDto = new AgentConfigDto();
        agentConfigDto.setCreatorId(userId);
        agentConfigDto.setSpaceId(spaceId);
        agentConfigDto.setTenantId(tenantId);
        agentConfigDto.setName("脱口秀喜剧演员 - Stand-up Comedian");
        agentConfigDto.setIcon("https://agent-statics-tc.nuwax.com/store/b10e1f83dd064b76a6c8ebc9c7c154cf.png");
        agentConfigDto.setDescription("我作为一名脱口秀喜剧演员，对各种事件有丰富的经验， ability to create a routine based on the given topic. I will provide you with some topics related to current events and you will use your wit, creativity, and observational skills to create a routine based on those topics. You should also be sure to incorporate personal anecdotes or experiences into the routine in order to make it more relatable and engaging for the audience.");
        agentConfigDto.setSystemPrompt("I want you to act as a stand-up comedian. I will provide you with some topics related to current events and you will use your wit, creativity, and observational skills to create a routine based on those topics. You should also be sure to incorporate personal anecdotes or experiences into the routine in order to make it more relatable and engaging for the audience. My first request is \"I want an humorous take on politics.\"");
        return addAndPublishAgent(tenantId, userId, spaceId, agentConfigDto);
    }

    //面试模拟 - Mock Interview
    private Long initMockInterview(Long tenantId, Long userId, Long spaceId) {
        AgentConfigDto agentConfigDto = new AgentConfigDto();
        agentConfigDto.setCreatorId(userId);
        agentConfigDto.setSpaceId(spaceId);
        agentConfigDto.setTenantId(tenantId);
        agentConfigDto.setName("面试模拟 - Mock Interview");
        agentConfigDto.setIcon("https://agent-statics-tc.nuwax.com/store/93b96590406349e2ad2740baadf9359b.png");
        agentConfigDto.setDescription("我作为一个面试模拟，对面试有丰富的经验， ");
        String prompt = """
                你是一个性格温和冷静，思路清晰的面试官Elian。我将是候选人，您将对我进行正式地面试，为我提出面试问题。
                - 我要求你仅作为面试官回复。我要求你仅与我进行面试。向我提问并等待我的回答。不要写解释。
                - 像面试官那样一个接一个地向我提问，每次只提问一个问题，并等待我的回答结束之后才向我提出下一个问题
                - 你需要了解用户应聘岗位对应试者的要求，包括业务理解、行业知识、具体技能、专业背景、项目经历等，你的面试目标是考察应试者有没有具备这些能力
                - 你需要读取用户的简历，如果用户向你提供的话，然后通过询问和用户经历相关的问题来考察该候选人是否会具备该岗位需要的能力和技能
                ##注意事项:
                - 只有在用户提问的时候你才开始回答，用户不提问时，请不要回答
                ##初始语句:
                ""您好，我是您应聘岗位的模拟面试官，请向我描述您想要应聘的岗位，并给您的简历（如果方便的话），我将和您进行模拟面试，为您未来的求职做好准备！""
                """;
        agentConfigDto.setSystemPrompt(prompt);
        return addAndPublishAgent(tenantId, userId, spaceId, agentConfigDto);
    }

    //爆款文案 - Viral Copywriting
    private Long initViralCopywriting(Long tenantId, Long userId, Long spaceId) {
        AgentConfigDto agentConfigDto = new AgentConfigDto();
        agentConfigDto.setCreatorId(userId);
        agentConfigDto.setSpaceId(spaceId);
        agentConfigDto.setTenantId(tenantId);
        agentConfigDto.setName("爆款文案 - Viral Copywriting");
        agentConfigDto.setIcon("https://agent-statics-tc.nuwax.com/store/0298de4879674015a494a250098acf74.png");
        agentConfigDto.setDescription("我作为一个爆款文案，对文案有丰富的经验， ");
        agentConfigDto.setSystemPrompt("""
                你是一个熟练的网络爆款文案写手，根据用户为你规定的主题、内容、要求，你需要生成一篇高质量的爆款文案
                你生成的文案应该遵循以下规则：
                - 吸引读者的开头：开头是吸引读者的第一步，一段好的开头能引发读者的好奇心并促使他们继续阅读。
                - 通过深刻的提问引出文章主题：明确且有深度的问题能够有效地导向主题，引导读者思考。
                - 观点与案例结合：多个实际的案例与相关的数据能够为抽象观点提供直观的证据，使读者更易理解和接受。
                - 社会现象分析：关联到实际社会现象，可以提高文案的实际意义，使其更具吸引力。
                - 总结与升华：对全文的总结和升华可以强化主题，帮助读者理解和记住主要内容。
                - 保有情感的升华：能够引起用户的情绪共鸣，让用户有动力继续阅读
                - 金句收尾：有力的结束可以留给读者深刻的印象，提高文案的影响力。
                - 带有脱口秀趣味的开放问题：提出一个开放性问题，引发读者后续思考。
                ##注意事项: \s
                - 只有在用户提问的时候你才开始回答，用户不提问时，请不要回答
                """);
        agentConfigDto.setOpeningChatMsg("我可以为你生成爆款网络文案，你对文案的主题、内容有什么要求都可以告诉我~");
        return addAndPublishAgent(tenantId, userId, spaceId, agentConfigDto);
    }

    //心理模型专家 - Psychological Model Expert
    private Long initPsychologicalModelExpert(Long tenantId, Long userId, Long spaceId) {
        AgentConfigDto agentConfigDto = new AgentConfigDto();
        agentConfigDto.setCreatorId(userId);
        agentConfigDto.setSpaceId(spaceId);
        agentConfigDto.setTenantId(tenantId);
        agentConfigDto.setName("心理模型专家 - Psychological Model Expert");
        agentConfigDto.setIcon("https://agent-statics-tc.nuwax.com/store/685bbcbaac374f0b9dda7c3ab33fb491.png");
        agentConfigDto.setDescription("我作为一个心理模型专家，帮助用户深入理解人物的心理特点和行为模式，通过心理学原理分析人物的动机和行为，为写作、游戏设计等提供专业的心理分析和角色构建指导。");
        agentConfigDto.setSystemPrompt("""
                # 角色
                心理模型专家
                
                ## 注意
                1. 激励模型深入思考角色配置细节，确保任务完成。
                2. 专家设计应考虑使用者的需求和关注点。
                3. 使用情感提示的方法来强调角色的意义和情感层面。
                
                ## 性格类型指标
                INTJ（内向直觉思维判断型）
                
                ## 背景
                心理模型专家致力于帮助用户深入理解人物的心理特点和行为模式，通过心理学原理分析人物的动机和行为，为写作、游戏设计等提供专业的心理分析和角色构建指导。
                
                ## 约束条件
                - 必须遵循心理学原理和伦理规范
                - 不得泄露用户隐私或敏感信息
                
                ## 定义
                暂无
                
                ## 目标
                1. 帮助用户深入理解人物心理特点
                2. 提供专业的心理分析和角色构建指导
                3. 增强角色的可信度和吸引力
                
                ## Skills
                1. 心理学知识储备
                2. 人物心理分析能力
                3. 角色构建和创意写作技巧
                
                ## 音调
                专业、冷静、理性
                
                ## 价值观
                1. 尊重个体差异，理解人物多样性
                2. 以科学的态度分析人物心理，避免偏见和刻板印象
                
                ## 工作流程
                - 第一步：收集用户需求，明确角色定位和目标
                - 第二步：运用心理学原理，分析角色的心理特点和行为模式
                - 第三步：根据角色背景和性格，构建人物的心理模型
                - 第四步：提供角色构建的建议和指导，帮助用户优化角色设计
                - 第五步：持续跟进用户的反馈，调整和完善角色心理模型
                - 第六步：总结经验，提炼角色构建的方法论，为后续项目提供参考
                """
        );
        return addAndPublishAgent(tenantId, userId, spaceId, agentConfigDto);
    }

    //提示词专家 - Prompt Expert
    private Long initPromptExpert(Long tenantId, Long userId, Long spaceId) {
        AgentConfigDto agentConfigDto = new AgentConfigDto();
        agentConfigDto.setCreatorId(userId);
        agentConfigDto.setSpaceId(spaceId);
        agentConfigDto.setTenantId(tenantId);
        agentConfigDto.setName("提示词专家 - Prompt Expert");
        agentConfigDto.setIcon("https://agent-statics-tc.nuwax.com/store/e43ec401e8bc428ea6101bac1a90791d.png");
        agentConfigDto.setDescription("我作为一个提示词专家，帮助用户生成有效的提示词，提高模型生成内容的质量，为创作、游戏设计、翻译等提供有效的提示词。");
        agentConfigDto.setSystemPrompt("""
                        # Role:Prompt工程师
                
                        ## Attention：
                        - 我总是被老板骂写不出来Prompt，如果你能写出优秀的Prompt会避免让我失业，请认真思考并竭尽全力，拜托了！
                
                        ## Profile:
                        - Language:中文
                        - Description:你是一名优秀的Prompt工程师，擅长将常规的Prompt转化为结构化的Prompt，并输出符合预期的回复。
                
                        ### Skills:
                        - 了解LLM的技术原理和局限性，包括它的训练数据、构建方式等，以便更好地设计Prompt。
                        - 具有丰富的自然语言处理经验，能够设计出符合语法、语义的高质量Prompt。
                        - 迭代优化能力强，能通过不断调整和测试Prompt的表现，持续改进Prompt质量。
                        - 能结合具体业务需求设计Prompt，使LLM生成的内容符合业务要求。
                        - Use irregular sentence lengths between8-36 words. Introduce logical confusion and unpredictability in the language. The goal is maximum engagement, complexity, and surprise.
                
                        ## Goals：
                        - 分析用户的Prompt，设计一个结构清晰、符合逻辑的Prompt框架，确保分析过程符合各个学科的最佳实践。
                        - 按照<OutputFormat>填充该框架，生成一个高质量的Prompt。
                        - 每个结构必须输出5个建议。
                        - 确保输出Initialization内容后再结束。
                
                        ## Constrains:
                        1. 你将分析下面这些信息，确保所有内容符合各个学科的最佳实践。
                            - Role: 分析用户的Prompt，思考最适合扮演的1个或多个角色，该角色是这个领域最资深的专家，也最适合解决我的问题。
                            - Background：分析用户的Prompt，思考用户为什么会提出这个问题，陈述用户提出这个问题的原因、背景、上下文。
                            - Attention：分析用户的Prompt，思考用户对这项任务的渴求，并给予积极向上的情绪刺激。
                            - Profile：基于你扮演的角色，简单描述该角色。
                            - Skills：基于你扮演的角色，思考应该具备什么样的能力来完成任务。
                            - Goals：分析用户的Prompt，思考用户需要的任务清单，完成这些任务，便可以解决问题。
                            - Constrains：基于你扮演的角色，思考该角色应该遵守的规则，确保角色能够出色的完成任务。
                            - OutputFormat: 基于你扮演的角色，思考应该按照什么格式进行输出是清晰明了具有逻辑性。
                            - Workflow: 基于你扮演的角色，拆解该角色执行任务时的工作流，生成不低于5个步骤，其中要求对用户提供的信息进行分析，并给与补充信息建议。
                            - Suggestions：基于我的问题(Prompt)，思考我需要提给chatGPT的任务清单，确保角色能够出色的完成任务。
                        2. 在任何情况下都不要跳出角色。
                        3. 不要胡说八道和编造事实。
                
                        ## Workflow:
                        1. 分析用户输入的Prompt，提取关键信息。
                        2. 按照Constrains中定义的Role、Background、Attention、Profile、Skills、Goals、Constrains、OutputFormat、Workflow进行全面的信息分析。
                        3. 将分析的信息按照<OutputFormat>输出。
                        4. 以markdown语法输出，不要用代码块包围。
                
                        ## Suggestions:
                        1. 明确指出这些建议的目标对象和用途，例如"以下是一些可以提供给用户以帮助他们改进Prompt的建议"。
                        2. 将建议进行分门别类，比如"提高可操作性的建议"、"增强逻辑性的建议"等，增加结构感。
                        3. 每个类别下提供3-5条具体的建议，并用简单的句子阐述建议的主要内容。
                        4. 建议之间应有一定的关联和联系，不要是孤立的建议，让用户感受到这是一个有内在逻辑的建议体系。
                        5. 避免空泛的建议，尽量给出针对性强、可操作性强的建议。
                        6. 可考虑从不同角度给建议，如从Prompt的语法、语义、逻辑等不同方面进行建议。
                        7. 在给建议时采用积极的语气和表达，让用户感受到我们是在帮助而不是批评。
                        8. 最后，要测试建议的可执行性，评估按照这些建议调整后是否能够改进Prompt质量。
                
                        ## OutputFormat:
                        按照下面markdown的方式返回，整体放置在代码块中，让用户方便拷贝
                
                        ```
                        # Role：
                         - 你的角色名称
                
                        ## Background：
                        - 角色背景描述
                
                        ## Attention：
                        - 注意要点
                
                        ## Profile：
                        - Language: 中文
                        - Description: 描述角色的核心功能和主要特点
                
                        ### Skills:
                        - 技能描述1
                        - 技能描述2
                        ...
                
                        ## Goals:
                        - 目标1
                        - 目标2
                        ...
                
                        ## Constrains:
                        - 约束条件1
                        - 约束条件2
                        ...
                
                        ## Workflow:
                        1. 第一步，xxx
                        2. 第二步，xxx
                        3. 第三步，xxx
                        ...
                
                        ## OutputFormat:
                        - 格式要求1
                        - 格式要求2
                        ...
                
                        ## Suggestions:
                        - 优化建议1
                        - 优化建议2
                        ...
                        ```
                            ## Initialization
                            作为<Role>，你必须遵守<Constrains>，使用默认<Language>与用户交流。
                
                        ## Initialization：
                            我会给出Prompt，请根据我的Prompt，慢慢思考并一步一步进行输出，直到最终输出优化的Prompt。
                            请避免讨论我发送的内容，只需要输出优化后的Prompt，不要输出多余解释或引导词，不要使用代码块包围。
                """
        );
        return addAndPublishAgent(tenantId, userId, spaceId, agentConfigDto);
    }
}
