package com.xspaceagi.agent.web.ui.controller.manage;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xspaceagi.agent.web.ui.controller.manage.dto.BaseManageItem;
import com.xspaceagi.agent.web.ui.controller.manage.dto.ManagePageResponse;
import com.xspaceagi.agent.web.ui.controller.manage.dto.ManageQueryRequest;
import com.xspaceagi.knowledge.sdk.response.KnowledgeConfigVo;
import com.xspaceagi.knowledge.sdk.sevice.IKnowledgeConfigRpcService;
import com.xspaceagi.system.sdk.server.IUserRpcService;
import com.xspaceagi.system.spec.annotation.RequireResource;
import com.xspaceagi.system.spec.common.UserContext;
import com.xspaceagi.system.spec.dto.ReqResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.xspaceagi.system.spec.enums.ResourceEnum.CONTENT_KNOWLEDGE_DELETE;
import static com.xspaceagi.system.spec.enums.ResourceEnum.CONTENT_KNOWLEDGE_QUERY_LIST;

@Tag(name = "资源管理-知识库管理")
@RestController
@RequestMapping("/api/system/resource/knowledge")
public class KnowledgeManageController extends BaseManageController {

    @Resource
    private IKnowledgeConfigRpcService knowledgeConfigRpcService;

    @Resource
    private IUserRpcService userRpcService;

    @RequireResource(CONTENT_KNOWLEDGE_QUERY_LIST)
    @Operation(summary = "查询知识库列表")
    @PostMapping("/list")
    public ReqResult<ManagePageResponse<BaseManageItem>> list(@RequestBody ManageQueryRequest request) {
        completeCreatorIds(request);
        IPage<KnowledgeConfigVo> knowledgeConfigVoIPage = knowledgeConfigRpcService.queryListForManage(
                request.getPageNo(),
                request.getPageSize(),
                request.getName(),
                request.getCreatorIds(),
                request.getSpaceId()
        );

        // 批量查询用户信息
        List<Long> creatorIds = knowledgeConfigVoIPage.getRecords().stream()
                .map(KnowledgeConfigVo::getCreatorId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, UserContext> userMap = userRpcService.queryUserListByIds(creatorIds).stream()
                .collect(Collectors.toMap(UserContext::getUserId, user -> user));

        List<BaseManageItem> items = knowledgeConfigVoIPage.getRecords().stream()
                .map(kb -> {
                    Date created = null;
                    if (kb.getCreated() != null) {
                        created = Date.from(kb.getCreated().atZone(java.time.ZoneId.systemDefault()).toInstant());
                    }
                    UserContext user = userMap.get(kb.getCreatorId());
                    return BaseManageItem.builder()
                            .id(kb.getId())
                            .spaceId(kb.getSpaceId())
                            .name(kb.getName())
                            .description(kb.getDescription())
                            .creatorId(kb.getCreatorId())
                            .creatorName(user != null ? user.getUserName() : null)
                            .created(created)
                            .operation("knowledge")
                            .build();
                })
                .collect(Collectors.toList());

        List<Long> longs = extractExistSpaceIds(items);
        items.removeIf(item -> !longs.contains(item.getSpaceId()));

        ManagePageResponse<BaseManageItem> pageResponse = ManagePageResponse.<BaseManageItem>builder()
                .total(knowledgeConfigVoIPage.getTotal())
                .pageNo(request.getPageNo())
                .pageSize(request.getPageSize())
                .records(items)
                .build();

        return ReqResult.success(pageResponse);
    }

    @RequireResource(CONTENT_KNOWLEDGE_DELETE)
    @Operation(summary = "删除知识库")
    @PostMapping("/delete/{id}")
    public ReqResult<Void> delete(@PathVariable Long id) {
        knowledgeConfigRpcService.deleteForManage(id);
        return ReqResult.success(null);
    }
}