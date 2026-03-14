package com.xspaceagi.agent.core.adapter.dto.config.plugin;

import com.xspaceagi.agent.core.adapter.dto.config.Arg;
import com.xspaceagi.agent.core.spec.enums.CodeLanguageEnum;
import com.xspaceagi.agent.core.spec.enums.PluginTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class PluginConfigDto implements Serializable {

    @Schema(hidden = true)
    private Long id;

    @Schema(hidden = true)
    private String name;

    @Schema(hidden = true)
    private String description;

    @Schema(hidden = true)
    private PluginTypeEnum type;

    @Schema(hidden = true)
    private CodeLanguageEnum codeLang;

    @Schema(description = "插件输入参数", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "插件输入参数不能为空")
    private List<Arg> inputArgs;

    @Schema(description = "插件输出参数", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "插件输出参数不能为空")
    private List<Arg> outputArgs;
}
