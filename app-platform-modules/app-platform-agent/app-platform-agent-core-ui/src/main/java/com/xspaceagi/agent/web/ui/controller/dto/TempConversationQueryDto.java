package com.xspaceagi.agent.web.ui.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TempConversationQueryDto {

    @Schema(description = "链接Key", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "chatKey不能为空")
    private String chatKey;

    @Schema(description = "会话唯一标识")
    private String conversationUid;

    private Long index;
    private Integer size;
}
