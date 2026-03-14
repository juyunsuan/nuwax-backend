package com.xspaceagi.custompage.ui.web.dto;

import java.io.Serializable;
import java.util.List;

import com.xspaceagi.agent.core.spec.enums.ModelApiProtocolEnum;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "模型响应体")
public class CustomPageModelRes implements Serializable {

    @Schema(description = "对话模型列表")
    private List<ModelDto> chatModelList;

    @Schema(description = "多模态模型列表")
    private List<ModelDto> multiModelList;

    @Schema(description = "模型信息")
    @Data
    public static class ModelDto implements Serializable {
        @Schema(description = "模型ID")
        private Long id;

        @Schema(description = "模型名称")
        private String name;

        @Schema(description = "模型描述")
        private String description;

        @Schema(description = "模型标识")
        private String model;

        @Schema(description = "模型接口协议，可选值：OpenAI, Ollama")
        private ModelApiProtocolEnum apiProtocol;

        @Schema(description = "租户ID")
        private Long tenantId;

        @Schema(description = "空间ID")
        private Long spaceId;

        private Integer isReasonModel;

        @Schema(description = "token上限")
        private Integer maxTokens;

    }
}
