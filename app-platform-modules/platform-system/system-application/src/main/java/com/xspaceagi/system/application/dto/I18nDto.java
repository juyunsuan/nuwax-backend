package com.xspaceagi.system.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import jakarta.validation.constraints.NotNull;


@Schema(description  = "I18nDto")
@Setter
@Getter
public class I18nDto {

     @Schema(description =  "业务模块标记，例如工会：union，超级节点：node", required = true)
    @NotNull(message = "业务模块标记不能为空")
    private String model;

    @NotNull(message = "业务模块记录ID不能为空")
     @Schema(description =  "业务模块具体记录ID，例如工会ID：unionId对应的值", required = true)
    private String mid;

    @NotNull(message = "语言不能为空")
     @Schema(description =  "具体语言，中文：zh-cn，英文：en-us", required = true)
    private String lang;

    @NotNull(message = "业务模块字段不能为空")
     @Schema(description =  "业务模块具体记录的具体字段，例如工会名称：unionName", required = true)
    private String fieldKey;

    @NotNull(message = "业务模块字段内容不能为空")
     @Schema(description =  "业务模块具体记录字段对应的内容", required = true)
    private String content;
}
