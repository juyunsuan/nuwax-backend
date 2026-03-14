package com.xspaceagi.custompage.ui.web.dto;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "查询开发服务器日志响应体")
public class GetDevLogRes {

    @Schema(description = "项目ID")
    private Long projectId;

    @Schema(description = "日志内容列表")
    private List<Map<String, Object>> logs;

    @Schema(description = "总行数")
    private Integer totalLines;

    @Schema(description = "当前起始行号")
    private Integer startIndex;

    @Schema(description = "是否命中缓存")
    private Boolean cacheHit;

    @Schema(description = "文件是否过大")
    private Boolean fileTooLarge;

    @Schema(description = "日志文件名")
    private String logFileName;

}