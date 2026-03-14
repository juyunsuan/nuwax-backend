package com.xspaceagi.custompage.ui.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "查询开发服务器日志请求体")
public class GetDevLogReq {

    @NotNull(message = "projectId 不能为空")
    @Schema(description = "项目ID")
    private Long projectId;

    @Schema(description = "起始行号")
    private Integer startIndex;

    @Schema(description = "日志类型(main-主日志,temp-临时日志)")
    private String logType;

}
