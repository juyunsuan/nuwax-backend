package com.xspaceagi.log.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xspaceagi.log.sdk.request.DocumentSearchRequest;
import com.xspaceagi.log.sdk.service.ISearchRpcService;
import com.xspaceagi.log.sdk.vo.LogDocument;
import com.xspaceagi.log.sdk.vo.SearchResult;
import com.xspaceagi.log.web.controller.base.BaseController;
import com.xspaceagi.log.web.controller.dto.LogDetailQueryDto;
import com.xspaceagi.log.web.controller.dto.LogQueryDto;
import com.xspaceagi.system.spec.annotation.RequireResource;
import com.xspaceagi.system.spec.common.RequestContext;
import com.xspaceagi.system.spec.dto.ReqResult;
import com.xspaceagi.system.spec.exception.LogPlatformException;
import com.xspaceagi.system.spec.page.PageQueryVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.xspaceagi.system.spec.enums.ResourceEnum.SYSTEM_RUNNING_LOG_QUERY_DETAIL;
import static com.xspaceagi.system.spec.enums.ResourceEnum.SYSTEM_RUNNING_LOG_QUERY_LIST;

@Tag(name = "日志平台管理端-搜索接口")
@RestController
@RequestMapping("/api/system/requestLogs")
@Slf4j
public class LogQueryManController extends BaseController {

    @Resource
    private ISearchRpcService iSearchRpcService;

    @RequireResource({SYSTEM_RUNNING_LOG_QUERY_LIST})
    @Operation(summary = "统一日志查询")
    @RequestMapping(path = "/list", method = RequestMethod.POST)
    public ReqResult<IPage<LogDocument>> search(@RequestBody PageQueryVo<LogQueryDto> pageQueryVo) {
        var filter = pageQueryVo.getQueryFilter();
        if (filter == null) {
            filter = new LogQueryDto();
        }
        if (pageQueryVo.getPageSize() == null || pageQueryVo.getPageSize() <= 0) {
            pageQueryVo.setPageSize(10L);
        }
        if (pageQueryVo.getCurrent() == null || pageQueryVo.getCurrent() <= 0) {
            pageQueryVo.setCurrent(1L);
        }
        // 避免到最后一页报错
        if (pageQueryVo.getCurrent() * pageQueryVo.getPageSize() > 10000) {
            pageQueryVo.setCurrent(pageQueryVo.getCurrent() - 1);
        }
        DocumentSearchRequest.DocumentSearchRequestBuilder builder = DocumentSearchRequest.builder();
        builder.searchDocumentClazz(LogDocument.class);
        builder.from((int) (pageQueryVo.getPageSize() * (pageQueryVo.getCurrent() - 1)));
        builder.size(pageQueryVo.getPageSize().intValue());
        List<Map<String, Object>> filterFieldsAndValues = new ArrayList<>();
        builder.filterFieldsAndValues(filterFieldsAndValues);
        filterFieldsAndValues.add(Map.of("tenantId", RequestContext.get().getTenantId()));
        if (StringUtils.isNoneBlank(filter.getInput())) {
            filterFieldsAndValues.add(Map.of("input", Map.of("express", "match", "query", filter.getInput())));
        }
        if (StringUtils.isNoneBlank(filter.getOutput())) {
            filterFieldsAndValues.add(Map.of("output", Map.of("express", "match", "query", filter.getOutput())));
        }
        if (StringUtils.isNoneBlank(filter.getProcessData())) {
            filterFieldsAndValues.add(Map.of("processData", filter.getProcessData()));
        }
        if (StringUtils.isNoneBlank(filter.getRequestId())) {
            filterFieldsAndValues.add(Map.of("requestId", filter.getRequestId()));
        }
        if (StringUtils.isNoneBlank(filter.getResultCode())) {
            filterFieldsAndValues.add(Map.of("resultCode", filter.getResultCode()));
        }
        if (StringUtils.isNoneBlank(filter.getConversationId())) {
            filterFieldsAndValues.add(Map.of("conversationId", filter.getConversationId()));
        }
        if (StringUtils.isNoneBlank(filter.getTargetId())) {
            filterFieldsAndValues.add(Map.of("targetId", filter.getTargetId()));
        }
        if (StringUtils.isNoneBlank(filter.getTargetName())) {
            filterFieldsAndValues.add(Map.of("targetName", filter.getTargetName()));
        }
        if (StringUtils.isNoneBlank(filter.getTargetType())) {
            filterFieldsAndValues.add(Map.of("targetType", filter.getTargetType()));
        }
        if (StringUtils.isNoneBlank(filter.getUserName())) {
            filterFieldsAndValues.add(Map.of("userName", filter.getUserName()));
        }
        if (StringUtils.isNoneBlank(filter.getFrom())) {
            filterFieldsAndValues.add(Map.of("from", filter.getFrom()));
        }
        if (filter.getUserId() != null) {
            filterFieldsAndValues.add(Map.of("userId", filter.getUserId()));
        }
        if (filter.getSpaceId() != null) {
            filterFieldsAndValues.add(Map.of("spaceId", filter.getSpaceId()));
        }
        Map<String, Object> createTimeRange = new HashMap<>();
        if (filter.getCreateTimeGt() != null) {
            createTimeRange.put("gt", filter.getCreateTimeGt());
        }
        if (filter.getCreateTimeLt() != null) {
            createTimeRange.put("lt", filter.getCreateTimeLt());
        }
        if (!createTimeRange.isEmpty()) {
            createTimeRange.put("express", "range");
            filterFieldsAndValues.add(Map.of("createTime", createTimeRange));
        }
        builder.sortFieldsAndValues(Map.of("createTime", "Desc"));
        var result = iSearchRpcService.search(builder.build());

        try {
            IPage<LogDocument> page = new Page<>(pageQueryVo.getCurrent().intValue(), pageQueryVo.getPageSize().intValue());
            page.setTotal(result.getTotal());
            page.setRecords(result.getItems().stream().map(item -> {
                LogDocument document = (LogDocument) item.getDocument();
                document.setProcessData(null);
                return document;
            }).collect(Collectors.toList()));
            return ReqResult.success(page);
        } catch (LogPlatformException e) {
            log.error("日志搜索失败: {}", e.getMessage(), e);
            return ReqResult.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("日志搜索发生未知错误", e);
            return ReqResult.error("日志搜索发生未知错误: " + e.getMessage());
        }
    }

    @RequireResource({SYSTEM_RUNNING_LOG_QUERY_DETAIL})
    @Operation(summary = "日志详情")
    @RequestMapping(path = "/detail", method = RequestMethod.POST)
    public ReqResult<LogDocument> detail(@RequestBody LogDetailQueryDto logDetailQueryDto) {

        DocumentSearchRequest.DocumentSearchRequestBuilder builder = DocumentSearchRequest.builder();
        builder.searchDocumentClazz(LogDocument.class);
        List<Map<String, Object>> filterFieldsAndValues = new ArrayList<>();
        builder.filterFieldsAndValues(filterFieldsAndValues);
        if (StringUtils.isNoneBlank(logDetailQueryDto.getId())) {
            filterFieldsAndValues.add(Map.of("id", logDetailQueryDto.getId()));
        }

        SearchResult search = iSearchRpcService.search(builder.build());
        LogDocument logDocument = search.getItems().stream().map(item -> (LogDocument) item.getDocument()).findFirst().orElse(null);
        if (logDocument == null) {
            return ReqResult.error("日志不存在");
        }
        return ReqResult.success(logDocument);
    }

}
