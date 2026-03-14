package com.xspaceagi.system.spec.exception;

import com.xspaceagi.system.spec.enums.ErrorCodeEnum;
import com.xspaceagi.system.spec.enums.HttpStatusEnum;

/**
 * 业务异常
 */
public class BizException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private int status;

    private String code = "0001";

    private String msg;

    public BizException(String msg) {
        super(msg);
        this.msg = msg;
    }

    public BizException(String code, String msg) {
        super(msg);
        this.code = code;
        this.msg = msg;
        this.status = HttpStatusEnum.OK.code();
    }

    public BizException(HttpStatusEnum status, ErrorCodeEnum code) {
        super(code.getMsg());
        this.code = code.getCode();
        this.msg = code.getMsg();
        this.status = status.code();
    }

    public BizException(HttpStatusEnum status, ErrorCodeEnum code, String message) {
        super(message);
        this.code = code.getCode();
        this.msg = message;
        this.status = status.code();
    }

    public String getCode() {
        return code;
    }

    public int getStatus() {
        return status;
    }
}
