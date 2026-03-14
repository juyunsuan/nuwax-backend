package com.xspaceagi.knowledge.domain.dto.qa;

import com.xspaceagi.agent.core.adapter.application.ModelApplicationService;
import com.xspaceagi.knowledge.domain.model.KnowledgeQaSegmentModel;
import com.xspaceagi.system.spec.utils.MySpringBeanContextUtil;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Builder
@Data
@Schema(description = "问答嵌入")
public class QAEmbeddingDto implements Serializable {

    @Schema(description = "知识库ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "知识库ID不能为空")
    private Long kbId;

    @Schema(description = "问答ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "问答ID不能为空")
    private Long qaId;

    @Schema(description = "文档ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "文档ID不能为空")
    private Long docId;

    @Schema(description = "归属分段对应的原始分段ID,可能没有", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Long rawId;

    @Schema(description = "归属分段对应的原始分段文本,可能没有", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String rawTxt;


    @Schema(description = "分段问题", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "分段问题不能为空")
    private String question;

    @Schema(description = "分段答案", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "分段答案不能为空")
    private String answer;

    @Schema(description = "嵌入向量", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "嵌入向量不能为空")
    private List<BigDecimal> embeddings;

    /**
     * qa转换为 milvus所需的参数,不会调用向量化接口,进行向量化
     *
     * @param qaModel 问答模型数据
     * @param rawText 问答对应原始的分段文本,如果有的话
     * @return
     */
    public static QAEmbeddingDto convertFromModelOnly(KnowledgeQaSegmentModel qaModel, String rawText) {
        return QAEmbeddingDto.builder()
                .qaId(qaModel.getId())
                .docId(qaModel.getDocId())
                .question(qaModel.getQuestion())
                .answer(qaModel.getAnswer())
                .rawId(qaModel.getRawId())
                .rawTxt(rawText)
                .embeddings(null)
                .kbId(qaModel.getKbId())
                .build();
    }

    /**
     * qa转换为 milvus所需的参数,且调用向量化接口,进行向量化
     *
     * @param qaModel 问答模型数据
     * @param rawText 问答对应原始的分段文本,如果有的话
     * @return
     */
    public static QAEmbeddingDto convertFromModelAndEmbedding(KnowledgeQaSegmentModel qaModel, String rawText,Long modelId) {

        var modelApplicationService = MySpringBeanContextUtil.get(ModelApplicationService.class);

        //向量化
        var question = List.of(qaModel.getQuestion());
        List<float[]> embeddingRes = modelApplicationService.embeddings(question,modelId);
        List<BigDecimal> embeddings = convertToBigDecimalList(embeddingRes.get(0));

        return QAEmbeddingDto.builder()
                .qaId(qaModel.getId())
                .docId(qaModel.getDocId())
                .question(qaModel.getQuestion())
                .answer(qaModel.getAnswer())
                .rawId(qaModel.getRawId())
                .rawTxt(rawText)
                .embeddings(embeddings)
                .kbId(qaModel.getKbId())
                .build();
    }

    public static List<BigDecimal> convertToBigDecimalList(float[] floats) {
        List<BigDecimal> bigDecimals = new ArrayList<>();
        for (float f : floats) {
            bigDecimals.add(BigDecimal.valueOf(f));
        }
        return bigDecimals;
    }

}
