package com.xspaceagi.custompage.ui.web.dto;

import com.xspaceagi.custompage.domain.dto.PageFileInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
public class PageFilesUpdateReq {

    @Schema(description = "项目ID")
    private Long projectId;

    @Schema(description = "文件列表")
    private List<PageFileInfo> files;

}
