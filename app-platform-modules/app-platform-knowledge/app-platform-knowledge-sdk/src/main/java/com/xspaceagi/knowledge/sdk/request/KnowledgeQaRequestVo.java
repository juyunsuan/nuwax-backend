package com.xspaceagi.knowledge.sdk.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@Schema(description = "知识库内问答查询")
public class KnowledgeQaRequestVo implements Serializable {

    @Schema(description = "知识库ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "知识库ID不能为空")
    private Long kbId;

    @Schema(description = "问题", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "问题不能为空")
    private String question;

    @Schema(description = "top-K值", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "top-K值不能为空")
    private int topK;

    @Schema(description = "是否忽略文档状态", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "是否忽略文档状态不能为空")
    private boolean ignoreDocStatus;

    private boolean ignoreTenantId;

}
