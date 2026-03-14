package com.xspaceagi.agent.core.application.service;

import com.alibaba.fastjson2.JSON;
import com.xspaceagi.agent.core.adapter.application.ModelApplicationService;
import com.xspaceagi.agent.core.adapter.application.PluginApplicationService;
import com.xspaceagi.agent.core.adapter.application.PublishApplicationService;
import com.xspaceagi.agent.core.adapter.dto.*;
import com.xspaceagi.agent.core.adapter.dto.config.Arg;
import com.xspaceagi.agent.core.adapter.dto.config.plugin.CodePluginConfigDto;
import com.xspaceagi.agent.core.adapter.dto.config.plugin.HttpPluginConfigDto;
import com.xspaceagi.agent.core.adapter.dto.config.plugin.PluginConfigDto;
import com.xspaceagi.agent.core.adapter.dto.config.plugin.PluginDto;
import com.xspaceagi.agent.core.adapter.repository.CopyIndexRecordRepository;
import com.xspaceagi.agent.core.adapter.repository.entity.ConfigHistory;
import com.xspaceagi.agent.core.adapter.repository.entity.PluginConfig;
import com.xspaceagi.agent.core.adapter.repository.entity.Published;
import com.xspaceagi.agent.core.domain.service.ConfigHistoryDomainService;
import com.xspaceagi.agent.core.domain.service.PluginDomainService;
import com.xspaceagi.agent.core.infra.component.ArgExtractUtil;
import com.xspaceagi.agent.core.infra.component.agent.AgentContext;
import com.xspaceagi.agent.core.infra.component.plugin.PluginContext;
import com.xspaceagi.agent.core.infra.component.plugin.PluginExecutor;
import com.xspaceagi.agent.core.spec.enums.DataTypeEnum;
import com.xspaceagi.agent.core.spec.enums.GlobalVariableEnum;
import com.xspaceagi.agent.core.spec.enums.PluginTypeEnum;
import com.xspaceagi.system.application.dto.UserDto;
import com.xspaceagi.system.application.service.UserApplicationService;
import com.xspaceagi.system.spec.common.RequestContext;
import com.xspaceagi.system.spec.exception.BizException;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class PluginApplicationServiceImpl implements PluginApplicationService {

    @Resource
    private PublishApplicationService publishApplicationService;

    @Resource
    private PluginDomainService pluginDomainService;

    @Resource
    private ConfigHistoryDomainService configHistoryDomainService;

    @Resource
    private UserApplicationService userApplicationService;

    @Resource
    private ModelApplicationService modelApplicationService;

    @Resource
    private CopyIndexRecordRepository copyIndexRecordRepository;

    @Resource
    private PluginExecutor pluginExecutor;

    @Override
    public Long add(PluginAddDto pluginAddDto) {
        PluginConfig pluginConfig = new PluginConfig();
        BeanUtils.copyProperties(pluginAddDto, pluginConfig);
        pluginDomainService.add(pluginConfig);
        addConfigHistory(pluginConfig.getId(), ConfigHistory.Type.Add, "新增插件");
        return pluginConfig.getId();
    }

    @Override
    public void update(PluginUpdateDto pluginUpdateDto) {
        PluginConfig pluginConfig = new PluginConfig();
        pluginConfig.setId(pluginUpdateDto.getId());
        pluginConfig.setIcon(pluginUpdateDto.getIcon());
        pluginConfig.setDescription(pluginUpdateDto.getDescription());
        pluginConfig.setName(pluginUpdateDto.getName());
        if (pluginUpdateDto.getConfig() != null) {
            pluginConfig.setConfig(JSON.toJSONString(pluginUpdateDto.getConfig()));
        }
        pluginDomainService.update(pluginConfig);
        addConfigHistory(pluginConfig.getId(), ConfigHistory.Type.Edit, "更新插件");
    }

    @Override
    public List<Arg> analysisPluginOutput(AnalysisHttpPluginOutputDto analysisHttpPluginOutputDto) {
        PluginDto pluginDto = queryById(analysisHttpPluginOutputDto.getPluginId());
        PluginConfigDto pluginConfigDto = (PluginConfigDto) pluginDto.getConfig();
        //用默认值作为参数
        Map<String, Object> params = analysisHttpPluginOutputDto.getParams();
        checkRequireAndSetDefaultValue(pluginConfigDto.getInputArgs(), params);
        pluginConfigDto.setOutputArgs(null);
        AgentContext agentContext = new AgentContext();
        agentContext.setUser((UserDto) RequestContext.get().getUser());
        agentContext.getVariableParams().put(GlobalVariableEnum.AGENT_ID.name(), -1L);
        agentContext.getVariableParams().put(GlobalVariableEnum.USER_UID.name(), agentContext.getUser().getUid());
        agentContext.getVariableParams().put(GlobalVariableEnum.SYS_USER_ID.name(), agentContext.getUser().getId());
        agentContext.getVariableParams().put(GlobalVariableEnum.USER_NAME.name(), agentContext.getUser().getUserName() == null ? agentContext.getUser().getNickName() : agentContext.getUser().getUserName());
        PluginContext pluginContext = PluginContext.builder()
                .agentContext(agentContext)
                .requestId(UUID.randomUUID().toString())
                .pluginConfig((PluginConfigDto) pluginDto.getConfig())
                .pluginDto(pluginDto)
                .params(params)
                .userId(RequestContext.get().getUserId())
                .test(false)
                .build();
        Mono<PluginExecuteResultDto> mono = pluginExecutor.execute(pluginContext);
        PluginExecuteResultDto resultDto = mono.block();
        if (!resultDto.isSuccess()) {
            throw new BizException(resultDto.getError());
        }
        if (resultDto.getResult() == null) {
            return List.of();
        }
        List<Arg> outputArgs = new ArrayList<>();
        resultToArgs(resultDto.getResult(), outputArgs);
        outputArgs.forEach(outputArg -> Arg.generateKey(outputArg.getName(), outputArg.getSubArgs(), null));
        return outputArgs;
    }

    private void resultToArgs(Object result, List<Arg> outputArgs) {
        if (result instanceof Map) {
            Map<String, Object> resultMap = (Map<String, Object>) result;
            resultMap.forEach((key, value) -> {
                Arg arg = new Arg();
                arg.setName(key);
                arg.setEnable(true);
                arg.setDescription(key);
                arg.setRequire(false);
                arg.setBindValueType(Arg.BindValueType.Input);
                if (value instanceof Map) {
                    arg.setDataType(DataTypeEnum.Object);
                    arg.setSubArgs(new ArrayList<>());
                    resultToArgs(value, arg.getSubArgs());
                } else if (value instanceof Integer) {
                    arg.setDataType(DataTypeEnum.Integer);
                } else if (value instanceof Number) {
                    arg.setDataType(DataTypeEnum.Number);
                } else if (value instanceof Boolean) {
                    arg.setDataType(DataTypeEnum.Boolean);
                } else if (value instanceof List) {
                    List<Object> list = (List<Object>) value;
                    if (list.size() > 0) {
                        if (list.get(0) instanceof Map) {
                            arg.setDataType(DataTypeEnum.Array_Object);
                            arg.setSubArgs(new ArrayList<>());
                            resultToArgs(list.get(0), arg.getSubArgs());
                        } else {
                            if (list.get(0) instanceof Integer) {
                                arg.setDataType(DataTypeEnum.Array_Integer);
                            } else if (list.get(0) instanceof Number) {
                                arg.setDataType(DataTypeEnum.Array_Number);
                            } else if (list.get(0) instanceof Boolean) {
                                arg.setDataType(DataTypeEnum.Array_Boolean);
                            } else {
                                arg.setDataType(DataTypeEnum.Array_String);
                            }
                        }
                    } else {
                        arg.setDataType(DataTypeEnum.Array_Object);
                    }
                } else {
                    arg.setDataType(DataTypeEnum.String);
                }
                outputArgs.add(arg);
            });
        }
    }

    private void checkRequireAndSetDefaultValue(List<Arg> inputArgs, Map<String, Object> params) {
        if (inputArgs == null || params == null) {
            return;
        }
        inputArgs.forEach(arg -> {
            boolean isNull = ArgExtractUtil.isArgBlankValue(arg, params);
            if (arg.isRequire() && isNull && StringUtils.isBlank(arg.getBindValue())) {
                throw new BizException("请填写必要参数[" + arg.getName() + "]，便于获取返回参数");
            }
            Object value = params.get(arg.getName());
            if (CollectionUtils.isNotEmpty(arg.getSubArgs())) {
                if (arg.getDataType() == DataTypeEnum.Object) {
                    if (value == null) {
                        value = new HashMap<>();
                    }
                    if (!(value instanceof Map)) {
                        throw new BizException("参数[" + arg.getName() + "]类型错误");
                    }
                    Map<String, Object> subParams = (Map<String, Object>) value;
                    checkRequireAndSetDefaultValue(arg.getSubArgs(), subParams);
                } else if (arg.getDataType() == DataTypeEnum.Array_Object) {
                    if (value == null) {
                        value = new ArrayList<>();
                    }
                    if (!(value instanceof List)) {
                        throw new BizException("参数[" + arg.getName() + "]类型错误");
                    }
                    List<Map<String, Object>> subParams = (List<Map<String, Object>>) value;
                    subParams.forEach(subParam -> checkRequireAndSetDefaultValue(arg.getSubArgs(), subParam));
                }
            } else {
                if (isNull) {
                    params.put(arg.getName(), arg.getBindValue());
                }
            }
        });
    }

    @Override
    public void delete(Long pluginId) {
        pluginDomainService.delete(pluginId);
    }

    @Override
    public Long copyPlugin(Long userId, Long pluginId) {
        PluginDto pluginDto = queryById(pluginId);
        return copyPlugin(userId, pluginDto, pluginDto.getSpaceId());
    }

    @Override
    public Long copyPlugin(Long userId, PluginDto pluginDto, Long targetSpaceId) {
        String newName = copyIndexRecordRepository.newCopyName("plugin", pluginDto.getSpaceId(), pluginDto.getName());
        PluginConfig copyPluginConfig = new PluginConfig();
        BeanUtils.copyProperties(pluginDto, copyPluginConfig);
        copyPluginConfig.setId(null);
        copyPluginConfig.setCreatorId(userId);
        copyPluginConfig.setModified(null);
        copyPluginConfig.setCreated(null);
        copyPluginConfig.setName(newName);
        copyPluginConfig.setPublishStatus(Published.PublishStatus.Developing);
        copyPluginConfig.setSpaceId(targetSpaceId);
        copyPluginConfig.setConfig(JSON.toJSONString(pluginDto.getConfig()));
        pluginDomainService.add(copyPluginConfig);
        try {
            return copyPluginConfig.getId();
        } finally {
            addConfigHistory(copyPluginConfig.getId(), ConfigHistory.Type.Add, "复制插件");
        }
    }

    @Override
    public void deleteBySpaceId(Long spaceId) {
        pluginDomainService.deleteBySpaceId(spaceId);
    }

    @Override
    public List<PluginDto> queryListBySpaceId(Long spaceId) {
        List<PluginConfig> pluginConfigs = pluginDomainService.queryListBySpaceId(spaceId);
        List<PluginDto> pluginDtos = pluginConfigs.stream().map(pluginConfig -> convertToPluginDto(pluginConfig)).collect(Collectors.toList());
        completeCreator(pluginDtos);
        return pluginDtos;
    }

    private void completeCreator(List<PluginDto> pluginDtos) {
        Map<Long, UserDto> userMap = userApplicationService.queryUserListByIds(pluginDtos.stream().map(PluginDto::getCreatorId).collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(UserDto::getId, userDto -> userDto));
        pluginDtos.forEach(pluginDto -> {
            //UserDto转CreatorDto
            UserDto userDto = userMap.get(pluginDto.getCreatorId());
            if (userDto != null) {
                CreatorDto creatorDto = CreatorDto.builder()
                        .userId(userDto.getId())
                        .nickName(userDto.getNickName())
                        .userName(userDto.getUserName())
                        .avatar(userDto.getAvatar())
                        .build();
                pluginDto.setCreator(creatorDto);
            }
        });
    }

    private PluginDto convertToPluginDto(PluginConfig pluginConfig) {
        if (pluginConfig == null) {
            return null;
        }
        PluginDto pluginDto = new PluginDto();
        BeanUtils.copyProperties(pluginConfig, pluginDto);
        pluginDto.setConfig(PluginDto.convertToPluginConfigDto(pluginDto, pluginConfig.getConfig()));
        return pluginDto;
    }

    @Override
    public List<PluginDto> queryListByIds(List<Long> pluginIds) {
        List<PluginConfig> pluginConfigs = pluginDomainService.queryListByIds(pluginIds);
        return pluginConfigs.stream().map(pluginConfig -> convertToPluginDto(pluginConfig)).collect(Collectors.toList());
    }

    @Override
    public PluginDto queryById(Long pluginId) {
        PluginDto pluginDto = convertToPluginDto(pluginDomainService.queryById(pluginId));
        PublishedDto publishedDto = publishApplicationService.queryPublished(Published.TargetType.Plugin, pluginId);
        if (pluginDto != null && publishedDto != null) {
            pluginDto.setPublishDate(publishedDto.getModified());
            pluginDto.setCategory(publishedDto.getCategory());
            pluginDto.setScope(publishedDto.getScope());
        }
        return pluginDto;
    }

    @Override
    public PluginDto queryPublishedPluginConfig(Long pluginId, Long spaceId) {
        PublishedDto publishedDto = publishApplicationService.queryPublished(Published.TargetType.Plugin, pluginId);
        if (publishedDto == null) {
            return null;
        }
        if (spaceId != null && publishedDto.getPublishedSpaceIds() != null && !publishedDto.getPublishedSpaceIds().contains(spaceId)) {
            return null;
        }
        PluginConfig pluginConfig = JSON.parseObject(publishedDto.getConfig(), PluginConfig.class);
        PluginDto pluginDto = convertToPluginDto(pluginConfig);
        pluginDto.setPublishDate(publishedDto.getModified());
        pluginDto.setPublishedSpaceIds(publishedDto.getPublishedSpaceIds());
        return pluginDto;
    }

    @Override
    public PluginExecuteResultDto execute(PluginExecuteRequestDto pluginExecuteRequestDto, PluginDto pluginDto) {
        AgentContext agentContext = new AgentContext();
        agentContext.setRequestId(pluginExecuteRequestDto.getRequestId());
        agentContext.setUser((UserDto) RequestContext.get().getUser());
        agentContext.getVariableParams().put(GlobalVariableEnum.AGENT_ID.name(), pluginExecuteRequestDto.getAgentId() == null ? -1L : pluginExecuteRequestDto.getAgentId());
        agentContext.getVariableParams().put(GlobalVariableEnum.AGENT_USER_MSG.name(), "");
        agentContext.getVariableParams().put(GlobalVariableEnum.REQUEST_ID.name(), pluginExecuteRequestDto.getRequestId());
        agentContext.getVariableParams().put(GlobalVariableEnum.CONVERSATION_ID.name(), pluginExecuteRequestDto.getRequestId());
        agentContext.getVariableParams().put(GlobalVariableEnum.SYS_USER_ID.name(), agentContext.getUser().getId());
        agentContext.getVariableParams().put(GlobalVariableEnum.USER_UID.name(), agentContext.getUser().getUid());
        agentContext.getVariableParams().put(GlobalVariableEnum.USER_NAME.name(), agentContext.getUser().getUserName() == null ? agentContext.getUser().getNickName() : agentContext.getUser().getUserName());
        PluginContext pluginContext = PluginContext.builder()
                .agentContext(agentContext)
                .requestId(pluginExecuteRequestDto.getRequestId())
                .pluginConfig((PluginConfigDto) pluginDto.getConfig())
                .pluginDto(pluginDto)
                .params(pluginExecuteRequestDto.getParams())
                .userId(RequestContext.get().getUserId())
                .test(pluginExecuteRequestDto.isTest())
                .build();
        Mono<PluginExecuteResultDto> mono = pluginExecutor.execute(pluginContext);
        return mono.block();
    }

    @Override
    public void checkSpacePluginPermission(Long spaceId, Long pluginId) {
        PublishedDto published = publishApplicationService.queryPublished(Published.TargetType.Plugin, pluginId);
        if (published == null) {
            PluginDto pluginDto = queryById(pluginId);
            if (pluginDto != null) {
                throw new BizException(String.format("插件[%s]未发布或已下线，请更换其他适合的插件", pluginDto.getName()));
            }
            throw new BizException("该插件未发布或已下线");
        }
        //为全局发布，不需要校验
        if (published.getScope() == Published.PublishScope.Tenant || published.getScope() == Published.PublishScope.Global) {
            return;
        }
        //为空时为全局发布，不需要校验
        if (published.getPublishedSpaceIds() == null) {
            return;
        }
        if (!published.getPublishedSpaceIds().contains(spaceId)) {
            throw new BizException("当前空间没有插件权限");
        }
    }

    @Override
    public List<String> validatePluginConfig(PluginDto pluginDto) {
        List<String> messages = new ArrayList<>();
        if (pluginDto.getConfig() == null) {
            messages.add("插件配置不能为空");
            return messages;
        }
        List<Arg> inputArgs = null;
        List<Arg> outputArgs = null;
        if (pluginDto.getType() == PluginTypeEnum.HTTP) {
            HttpPluginConfigDto httpPluginConfigDto = (HttpPluginConfigDto) pluginDto.getConfig();
            if (StringUtils.isBlank(httpPluginConfigDto.getUrl())) {
                messages.add("URL不能为空");
            } else if (!httpPluginConfigDto.getUrl().startsWith("http://") && !httpPluginConfigDto.getUrl().startsWith("https://")) {
                //URL是否符合规范
                messages.add("URL格式不正确");
            }
            if (httpPluginConfigDto.getMethod() == null) {
                messages.add("请求方法不能为空");
            }
            if (httpPluginConfigDto.getTimeout() == null || httpPluginConfigDto.getTimeout() <= 0) {
                messages.add("超时时间不能为空或小于等于0");
            }
            inputArgs = httpPluginConfigDto.getInputArgs();
            outputArgs = httpPluginConfigDto.getOutputArgs();
        } else if (pluginDto.getType() == PluginTypeEnum.CODE) {
            CodePluginConfigDto codePluginConfigDto = (CodePluginConfigDto) pluginDto.getConfig();
            inputArgs = codePluginConfigDto.getInputArgs();
            outputArgs = codePluginConfigDto.getOutputArgs();
            if (StringUtils.isBlank(codePluginConfigDto.getCode())) {
                messages.add("代码不能为空");
            }
        }
        //校验参数配置
        if (inputArgs != null) {
            validArg(inputArgs, messages, true);
        }
        if (outputArgs != null) {
            validArg(outputArgs, messages, false);
        }
        //代码安全性检测
        if (pluginDto.getType() == PluginTypeEnum.CODE) {
            CodePluginConfigDto codePluginConfigDto = (CodePluginConfigDto) pluginDto.getConfig();
            CodeCheckResultDto codeCheckResultDto = modelApplicationService.codeSaleCheck(codePluginConfigDto.getCode());
            if (codeCheckResultDto != null && codeCheckResultDto.getPass() != null && !codeCheckResultDto.getPass()) {
                messages.add(codeCheckResultDto.getReason());
            }
        }
        return messages;
    }

    private void validArg(List<Arg> args, List<String> messages, boolean isInput) {
        if (args != null) {
            Set<String> names = new HashSet<>();
            args.forEach(arg -> {
                if (names.contains(arg.getName())) {
                    messages.add("参数[" + arg.getName() + "]重复，同级参数不能取名相同");
                } else {
                    names.add(arg.getName());
                }
                if (StringUtils.isBlank(arg.getName())) {
                    messages.add("参数名不能为空");
                    arg.setName("");
                }
                //参数命名规范校验
                if (!arg.getName().matches("^[a-zA-Z_][a-zA-Z0-9_-]*$")) {
                    messages.add("参数名[" + arg.getName() + "]不符合命名规范");
                }
                if (isInput && StringUtils.isBlank(arg.getDescription())) {
                    messages.add("参数[" + arg.getName() + "]对应的描述信息不能为空");
                }
                if (arg.getDataType() == null) {
                    messages.add("参数类型不能为空");
                }
                validArg(arg.getSubArgs(), messages, isInput);
            });
        }
    }

    private void addConfigHistory(Long pluginId, ConfigHistory.Type type, String description) {
        PluginDto pluginDto = queryById(pluginId);
        ConfigHistory configHistory = ConfigHistory.builder()
                .config(JSON.toJSONString(pluginDto))
                .targetId(pluginId)
                .description(description)
                .targetType(Published.TargetType.Plugin)
                .opUserId(RequestContext.get().getUserId())
                .type(type)
                .build();
        configHistoryDomainService.addConfigHistory(configHistory);
    }
}
