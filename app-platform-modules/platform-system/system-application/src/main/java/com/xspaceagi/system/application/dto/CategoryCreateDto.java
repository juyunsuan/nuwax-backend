package com.xspaceagi.system.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 分类创建请求DTO
 */
@Data
@Schema(description = "分类创建请求")
public class CategoryCreateDto {

    @NotBlank(message = "分类名称不能为空")
    @Schema(description = "分类名称", required = true)
    private String name;

    @NotBlank(message = "分类编码不能为空")
    @Schema(description = "分类编码", required = true)
    private String code;

    @NotBlank(message = "分类类型不能为空")
    @Schema(description = "分类类型：Agent、PageApp、Component", required = true)
    private String type;

    @Schema(description = "分类描述")
    private String description;
}
