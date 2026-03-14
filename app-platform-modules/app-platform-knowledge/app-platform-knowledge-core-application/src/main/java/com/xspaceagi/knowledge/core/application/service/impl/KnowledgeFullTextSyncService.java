package com.xspaceagi.knowledge.core.application.service.impl;

import com.xspaceagi.knowledge.core.adapter.client.dto.PushResult;
import com.xspaceagi.knowledge.core.application.service.IKnowledgeFullTextSyncService;
import com.xspaceagi.knowledge.core.application.translator.KnowledgeFullTextSearchTranslator;
import com.xspaceagi.knowledge.domain.model.KnowledgeConfigModel;
import com.xspaceagi.knowledge.domain.model.KnowledgeDocumentModel;
import com.xspaceagi.knowledge.domain.model.KnowledgeRawSegmentModel;
import com.xspaceagi.knowledge.domain.model.fulltext.RawSegmentFullTextModel;
import com.xspaceagi.knowledge.domain.repository.IKnowledgeConfigRepository;
import com.xspaceagi.knowledge.domain.repository.IKnowledgeDocumentRepository;
import com.xspaceagi.knowledge.domain.repository.IKnowledgeRawSegmentRepository;
import com.xspaceagi.knowledge.domain.service.IKnowledgeFullTextSearchDomainService;
import com.xspaceagi.system.spec.common.UserContext;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * 知识库全文检索增量同步服务实现
 * 
 * @author system
 * @date 2025-03-31
 */
@Slf4j
@Service
public class KnowledgeFullTextSyncService implements IKnowledgeFullTextSyncService {

    @Resource
    private IKnowledgeFullTextSearchDomainService fullTextSearchDomainService;

    @Resource
    private IKnowledgeRawSegmentRepository rawSegmentRepository;

    @Resource
    private IKnowledgeDocumentRepository documentRepository;

    @Resource
    private IKnowledgeConfigRepository knowledgeConfigRepository;

    @Resource
    private KnowledgeFullTextSearchTranslator translator;

    /**
     * 文档分段完成后，同步到 Quickwit
     * 
     * @param docId 文档ID
     * @param userContext 用户上下文
     */
    public void syncDocumentToQuickwit(Long docId, UserContext userContext) {
        log.info("同步文档到全文检索: docId={}, userId={}", docId, userContext.getUserId());

        // 1. 查询文档信息
        KnowledgeDocumentModel doc = documentRepository.queryOneInfoById(docId);
        if (doc == null) {
            log.warn("文档不存在: docId={}", docId);
            return;
        }

        Long kbId = doc.getKbId();
        Long tenantId = userContext.getTenantId();

        // 2. 查询知识库信息获取空间ID
        KnowledgeConfigModel config = knowledgeConfigRepository.queryOneInfoById(kbId);
        if (config == null) {
            log.warn("知识库不存在: kbId={}", kbId);
            return;
        }
        Long spaceId = config.getSpaceId();

        // 3. 查询该文档的所有分段
        List<KnowledgeRawSegmentModel> rawSegments = rawSegmentRepository.queryListByDocId(docId);

        if (CollectionUtils.isEmpty(rawSegments)) {
            log.warn("文档没有分段数据: docId={}", docId);
            return;
        }

        // 4. 转换为全文检索模型
        List<RawSegmentFullTextModel> fullTextModels = 
            translator.toFullTextModelList(rawSegments, tenantId, spaceId);

        // 5. 批量推送到 Quickwit
       PushResult pushResult =
            fullTextSearchDomainService.pushSegments(fullTextModels);

        log.info("文档全文检索数据同步完成: docId={}, segmentCount={}, indexedCount={}", 
            docId, rawSegments.size(), pushResult != null ? pushResult.getIndexedCount() : 0);
    }

    /**
     * 删除文档的全文检索数据
     * 
     * @param docId 文档ID
     * @param kbId 知识库ID
     * @param userContext 用户上下文
     */
    public void deleteDocumentFromQuickwit(Long docId, Long kbId, UserContext userContext) {
        log.info("删除文档全文检索数据: docId={}, kbId={}, userId={}", docId, kbId, userContext.getUserId());

        Long deletedCount = fullTextSearchDomainService.deleteByDocId(docId, kbId, userContext.getTenantId());

        log.info("删除文档全文检索数据完成: docId={}, kbId={}, deletedCount={}", docId, kbId, deletedCount);
    }

    /**
     * 更新分段文本
     * 
     * @param rawId 分段ID
     * @param newText 新文本
     * @param userContext 用户上下文
     */
    public void updateSegmentText(Long rawId, String newText, UserContext userContext) {
        log.info("更新分段文本: rawId={}, userId={}", rawId, userContext.getUserId());

        // 查询分段信息获取空间ID
        KnowledgeRawSegmentModel segment = rawSegmentRepository.queryOneInfoById(rawId);
        if (segment == null) {
            log.warn("分段不存在: rawId={}", rawId);
            return;
        }

        // 查询知识库信息获取空间ID
        KnowledgeConfigModel config = knowledgeConfigRepository.queryOneInfoById(segment.getKbId());
        Long spaceId = config != null ? config.getSpaceId() : null;

        Long updatedCount = fullTextSearchDomainService.updateSegmentText(
            rawId, 
            newText, 
            userContext.getTenantId(), 
            spaceId
        );

        log.info("更新分段文本完成: rawId={}, updatedCount={}", rawId, updatedCount);
    }

    /**
     * 删除知识库的全文检索数据
     * 
     * @param kbId 知识库ID
     * @param userContext 用户上下文
     */
    public void deleteKnowledgeBaseFromQuickwit(Long kbId, UserContext userContext) {
        log.info("删除知识库全文检索数据: kbId={}, userId={}", kbId, userContext.getUserId());

        Long deletedCount = fullTextSearchDomainService.deleteByKbId(kbId, userContext.getTenantId());

        log.info("删除知识库全文检索数据完成: kbId={}, deletedCount={}", kbId, deletedCount);
    }

    /**
     * 删除指定分段的全文检索数据
     * 
     * @param rawSegmentIds 分段ID列表
     * @param userContext 用户上下文
     */
    public void deleteSegmentsFromQuickwit(List<Long> rawSegmentIds, UserContext userContext) {
        if (CollectionUtils.isEmpty(rawSegmentIds)) {
            log.warn("删除分段ID列表为空");
            return;
        }

        log.info("删除分段全文检索数据: rawIds={}, userId={}", rawSegmentIds, userContext.getUserId());

        Long deletedCount = fullTextSearchDomainService.deleteByRawIds(rawSegmentIds, userContext.getTenantId());

        log.info("删除分段全文检索数据完成: rawIds={}, deletedCount={}", rawSegmentIds, deletedCount);
    }
}
