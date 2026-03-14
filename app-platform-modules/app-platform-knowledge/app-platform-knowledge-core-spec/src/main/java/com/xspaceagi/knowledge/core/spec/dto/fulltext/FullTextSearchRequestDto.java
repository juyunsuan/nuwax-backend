package com.xspaceagi.knowledge.core.spec.dto.fulltext;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 全文检索请求 DTO
 * 
 * @author system
 * @date 2025-03-31
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FullTextSearchRequestDto {

    /**
     * 知识库ID
     */
    @NotNull(message = "知识库ID不能为空")
    private Long kbId;

    /**
     * 查询文本（自然语言）
     */
    @NotBlank(message = "查询文本不能为空")
    private String queryText;

    /**
     * 返回结果数量（Top-K），默认 10
     */
    @Min(value = 1, message = "返回结果数量至少为1")
    @Max(value = 100, message = "返回结果数量最多为100")
    @Builder.Default
    private Integer topK = 10;

    /**
     * 指定文档ID列表（可选）
     */
    private List<Long> docIds;

    /**
     * BM25 参数：忽略低重要性词的比例（0.0-1.0），默认 0.2
     */
    @Min(value = 0, message = "dropRatioSearch范围0.0-1.0")
    @Max(value = 1, message = "dropRatioSearch范围0.0-1.0")
    @Builder.Default
    private Float dropRatioSearch = 0.2f;

    /**
     * 是否忽略文档发布状态，默认 false
     */
    @Builder.Default
    private Boolean ignoreDocStatus = false;

}

