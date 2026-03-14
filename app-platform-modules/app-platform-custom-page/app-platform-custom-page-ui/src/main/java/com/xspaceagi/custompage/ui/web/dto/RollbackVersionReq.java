package com.xspaceagi.custompage.ui.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class RollbackVersionReq {

    @Schema(description = "项目ID")
    private Long projectId;

    @Schema(description = "回滚到的版本号")
    private Integer rollbackTo;

}

