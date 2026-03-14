package com.xspaceagi.custompage.sdk.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum CustomPageActionEnum {

    CREATE_PROJECT("create_project", "创建项目"),
    UPLOAD("upload", "上传项目"),
    SUBMIT_FILES_UPDATE("submit_files_update", "提交文件更新"),
    UPLOAD_SINGLE_FILE("upload_single_file", "上传单个文件"),
    CHAT("chat", "聊天"),
    ROLLBACK_VERSION("rollback_version", "回滚版本");

    private String code;
    private String name;

}