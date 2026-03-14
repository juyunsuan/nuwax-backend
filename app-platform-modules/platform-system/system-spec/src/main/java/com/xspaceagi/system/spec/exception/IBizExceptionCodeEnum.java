package com.xspaceagi.system.spec.exception;


public interface IBizExceptionCodeEnum {

    String getCode();
    /**
     * 错误信息
     */
    String getMessage();
    /**
     * 分类备注,方便看异常定义,不参与错误提示
     */
    String getRemark();
}
