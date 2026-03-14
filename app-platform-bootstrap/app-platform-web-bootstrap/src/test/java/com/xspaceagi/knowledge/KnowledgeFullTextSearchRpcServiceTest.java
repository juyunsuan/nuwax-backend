package com.xspaceagi.knowledge;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.xspaceagi.system.spec.common.RequestContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import com.xspaceagi.knowledge.sdk.request.KnowledgeFullTextSearchRequestVo;
import com.xspaceagi.knowledge.sdk.response.KnowledgeFullTextSearchResponseVo;
import com.xspaceagi.knowledge.sdk.sevice.IKnowledgeFullTextSearchRpcService;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

/**
 * 知识库全文检索 RPC 服务测试
 * 
 * @author system
 * @date 2025-03-31
 */
@Slf4j
@SpringBootTest
public class KnowledgeFullTextSearchRpcServiceTest {

    @Resource
    private IKnowledgeFullTextSearchRpcService knowledgeFullTextSearchRpcService;

    /**
     * 测试知识库全文检索功能 - 单知识库检索
     */
    @Test
    public void testFullTextSearch() {
        try {
            RequestContext.setThreadTenantId(1L);
            
            // Arrange
            KnowledgeFullTextSearchRequestVo requestVo = new KnowledgeFullTextSearchRequestVo();
            requestVo.setKbIds(java.util.Arrays.asList(411L));
            requestVo.setTenantId(1L);
            requestVo.setQueryText("智能体");
            requestVo.setTopK(10);
            
            log.info("开始全文检索测试: kbIds={}, queryText={}, topK={}", 
                requestVo.getKbIds(), requestVo.getQueryText(), requestVo.getTopK());
            
            // Act
            KnowledgeFullTextSearchResponseVo responseVo = knowledgeFullTextSearchRpcService.search(requestVo);
            
            // Assert
            assertNotNull(responseVo, "返回结果不能为空");
            assertNotNull(responseVo.getResults(), "结果列表不能为空");
            assertTrue(responseVo.getCostTimeMs() >= 0, "耗时应该大于等于0");
            
            log.info("\n\n========================================");
            log.info("=== 全文检索测试结果 ===");
            log.info("========================================");
            log.info("检索结果数量: {}", responseVo.getResults().size());
            log.info("耗时: {}ms", responseVo.getCostTimeMs());
            log.info("========================================\n");
            
            // 打印前3个结果详情
            if (!responseVo.getResults().isEmpty()) {
                log.info("\n=== 前3个结果详情 ===");
                int index = 1;
                for (var result : responseVo.getResults().stream().limit(3).toList()) {
                    String textPreview = result.getRawText() != null && result.getRawText().length() > 100 
                        ? result.getRawText().substring(0, 100) + "..." 
                        : result.getRawText();
                    
                    log.info("\n[结果 #{}]", index++);
                    log.info("  分段ID (rawSegmentId): {}", result.getRawSegmentId());
                    log.info("  文档ID (docId): {}", result.getDocId());
                    log.info("  知识库ID (kbId): {}", result.getKbId());
                    log.info("  BM25评分 (score): {}", result.getScore());
                    log.info("  排序索引 (sortIndex): {}", result.getSortIndex());
                    log.info("  文档名称 (documentName): {}", result.getDocumentName());
                    log.info("  文本内容: {}", textPreview);
                }
                log.info("\n========================================\n");
            } else {
                log.warn("\n⚠️  没有找到任何匹配的结果！\n");
            }
            
        } finally {
            RequestContext.remove();
        }
    }

    /**
     * 测试全文检索 - 多知识库检索
     */
    @Test
    public void testFullTextSearchMultipleKbs() {
        try {
            RequestContext.setThreadTenantId(1L);
            
            // Arrange - 测试多个知识库
            KnowledgeFullTextSearchRequestVo requestVo = new KnowledgeFullTextSearchRequestVo();
            requestVo.setKbIds(java.util.Arrays.asList(200L, 113L)); // 多个知识库
            requestVo.setTenantId(1L);
            requestVo.setQueryText("Python");
            requestVo.setTopK(5);
            
            log.info("开始多知识库全文检索测试: kbIds={}, queryText={}, topK={}", 
                requestVo.getKbIds(), requestVo.getQueryText(), requestVo.getTopK());
            
            // Act
            KnowledgeFullTextSearchResponseVo responseVo = knowledgeFullTextSearchRpcService.search(requestVo);
            
            // Assert
            assertNotNull(responseVo);
            assertNotNull(responseVo.getResults());
            
            log.info("\n\n========================================");
            log.info("=== 多知识库检索测试结果 ===");
            log.info("========================================");
            log.info("检索结果数量: {}", responseVo.getResults().size());
            log.info("耗时: {}ms", responseVo.getCostTimeMs());
            
            // 统计各知识库的结果数量
            if (!responseVo.getResults().isEmpty()) {
                java.util.Map<Long, Long> kbCountMap = responseVo.getResults().stream()
                    .collect(java.util.stream.Collectors.groupingBy(
                        result -> result.getKbId(),
                        java.util.stream.Collectors.counting()
                    ));
                
                log.info("\n=== 各知识库结果分布 ===");
                kbCountMap.forEach((kbId, count) -> 
                    log.info("知识库ID {} 的结果数量: {}", kbId, count));
                log.info("========================================\n");
            } else {
                log.warn("\n⚠️  没有找到任何匹配的结果！\n");
            }
            
        } finally {
            RequestContext.remove();
        }
    }

    /**
     * 测试全文检索 - 指定文档ID检索
     */
    @Test
    public void testFullTextSearchWithDocIds() {
        try {
            RequestContext.setThreadTenantId(1L);
            
            // Arrange - 测试指定文档ID检索
            KnowledgeFullTextSearchRequestVo requestVo = new KnowledgeFullTextSearchRequestVo();
            requestVo.setKbIds(java.util.Arrays.asList(200L));
            requestVo.setTenantId(1L);
            requestVo.setQueryText("Python");
            requestVo.setTopK(10);
            requestVo.setDocIds(java.util.Arrays.asList(1L, 2L, 3L)); // 指定文档ID
            
            log.info("开始指定文档检索测试: kbIds={}, docIds={}, queryText={}", 
                requestVo.getKbIds(), requestVo.getDocIds(), requestVo.getQueryText());
            
            // Act
            KnowledgeFullTextSearchResponseVo responseVo = knowledgeFullTextSearchRpcService.search(requestVo);
            
            // Assert
            assertNotNull(responseVo);
            assertNotNull(responseVo.getResults());
            
            log.info("\n\n========================================");
            log.info("=== 指定文档检索测试结果 ===");
            log.info("========================================");
            log.info("检索结果数量: {}", responseVo.getResults().size());
            log.info("耗时: {}ms", responseVo.getCostTimeMs());
            
            // 验证所有结果都来自指定的文档
            if (!responseVo.getResults().isEmpty()) {
                boolean allFromSpecifiedDocs = responseVo.getResults().stream()
                    .allMatch(result -> requestVo.getDocIds().contains(result.getDocId()));
                
                log.info("\n所有结果是否来自指定文档: {}", allFromSpecifiedDocs);
                log.info("========================================\n");
            } else {
                log.warn("\n⚠️  没有找到任何匹配的结果！\n");
            }
            
        } finally {
            RequestContext.remove();
        }
    }

    /**
     * 测试全文检索 - 空结果场景
     */
    @Test
    public void testFullTextSearchWithNoResults() {
        try {
            RequestContext.setThreadTenantId(1L);
            
            // Arrange - 使用不存在的知识库ID
            KnowledgeFullTextSearchRequestVo requestVo = new KnowledgeFullTextSearchRequestVo();
            requestVo.setKbIds(java.util.Arrays.asList(999999L));
            requestVo.setTenantId(1L);
            requestVo.setQueryText("不存在的内容XYZABC123");
            requestVo.setTopK(10);
            
            log.info("开始空结果测试: kbIds={}, queryText={}", 
                requestVo.getKbIds(), requestVo.getQueryText());
            
            // Act
            KnowledgeFullTextSearchResponseVo responseVo = knowledgeFullTextSearchRpcService.search(requestVo);
            
            // Assert
            assertNotNull(responseVo);
            assertNotNull(responseVo.getResults());
            
            log.info("\n\n========================================");
            log.info("=== 空结果测试 ===");
            log.info("========================================");
            log.info("检索结果数量: {}", responseVo.getResults().size());
            log.info("耗时: {}ms", responseVo.getCostTimeMs());
            log.info("========================================\n");
            
        } finally {
            RequestContext.remove();
        }
    }

    /**
     * 测试全文检索 - 不同TopK值
     */
    @Test
    public void testFullTextSearchWithDifferentTopK() {
        try {
            RequestContext.setThreadTenantId(1L);
            
            int[] topKValues = {1, 5, 10, 20};
            
            for (int topK : topKValues) {
                // Arrange
                KnowledgeFullTextSearchRequestVo requestVo = new KnowledgeFullTextSearchRequestVo();
                requestVo.setKbIds(java.util.Arrays.asList(200L));
                requestVo.setTenantId(1L);
                requestVo.setQueryText("Python");
                requestVo.setTopK(topK);
                
                log.info("测试TopK={}: kbIds={}, queryText={}", 
                    topK, requestVo.getKbIds(), requestVo.getQueryText());
                
                // Act
                KnowledgeFullTextSearchResponseVo responseVo = knowledgeFullTextSearchRpcService.search(requestVo);
                
                // Assert
                assertNotNull(responseVo);
                assertNotNull(responseVo.getResults());
                assertTrue(responseVo.getResults().size() <= topK, 
                    "返回结果数量不应超过TopK值");
                
                log.info("TopK={} 结果: 返回{}条, 耗时{}ms", 
                    topK, responseVo.getResults().size(), responseVo.getCostTimeMs());
            }
            
        } finally {
            RequestContext.remove();
        }
    }
}
