package com.xspaceagi.agent.core.adapter.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

@Data
public class ConversationUpdateDto implements Serializable {

    @Schema(description = "会话ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "会话ID不能为空")
    private Long id;

    @Schema(description = "用户第一条消息")
    @NotNull(message = "用户第一条消息")
    private String firstMessage;

    @Schema(description = "会话主题，可以不传，firstMessage与topic二选一")
    private String topic;
}
