package com.xspaceagi.interceptor;

import com.xspaceagi.system.spec.dto.ReqResult;
import com.xspaceagi.system.spec.enums.ErrorCodeEnum;
import com.xspaceagi.system.spec.exception.*;
import jakarta.validation.UnexpectedTypeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@ControllerAdvice
public class AppExceptionHandler {

    /**
     * 处理异常消息,如果是业务异常将通过json的格式返回，否则根据模式来处理异常的显示.
     */
    @ExceptionHandler
    @ResponseBody
    public ReqResult<?> processException(Exception ex) {
        ReqResult<?> responseData = ReqResult.error(ex.getMessage());
        if (ex instanceof BizException bizException) {
            responseData = ReqResult.error(bizException.getCode(), bizException.getMessage());
        } else {

            if (ex instanceof SpacePermissionException) {
                log.warn("异常[SpacePermissionException] ", ex);
                responseData = ReqResult.error(ErrorCodeEnum.PERMISSION_DENIED.getCode(),
                        ErrorCodeEnum.PERMISSION_DENIED.getMsg());
            } else if (ex instanceof ResourcePermissionException) {
                responseData = ReqResult.error(ErrorCodeEnum.RESOURCE_PERMISSION_DENIED.getCode(),
                        ErrorCodeEnum.RESOURCE_PERMISSION_DENIED.getMsg());
            } else if (ex instanceof HttpRequestMethodNotSupportedException) {
                log.warn("异常[HttpRequestMethodNotSupportedException] ", ex);
                responseData = ReqResult.error(ErrorCodeEnum.METHOD_NOT_ALLOWED.getCode(), ex.getMessage());
            } else if (ex instanceof MissingServletRequestParameterException) {
                log.warn("异常[MissingServletRequestParameterException] ", ex);
                responseData = ReqResult.error(ErrorCodeEnum.INVALID_PARAM.getCode(), ex.getMessage());
            } else if (ex instanceof HttpMessageNotReadableException) {
                log.warn("异常[HttpMessageNotReadableException] ", ex);
                responseData = ReqResult.error(ErrorCodeEnum.INVALID_PARAM.getCode(), ex.getMessage());
            } else if (ex instanceof MethodArgumentNotValidException methodArgumentNotValidException) {
                log.warn("异常[MethodArgumentNotValidException] ", ex);
                String msg = methodArgumentNotValidException.getBindingResult().getFieldError().getDefaultMessage();
                responseData = ReqResult.error(ErrorCodeEnum.INVALID_PARAM.getCode(), msg);
            } else if (ex instanceof UnexpectedTypeException) {
                log.warn("异常[UnexpectedTypeException] ", ex);
                responseData = ReqResult.error(ErrorCodeEnum.INVALID_PARAM.getCode(), ex.getMessage());
            } else if (ex instanceof HttpMediaTypeNotSupportedException) {
                log.warn("异常[HttpMediaTypeNotSupportedException] ", ex);
                responseData = ReqResult.error(ErrorCodeEnum.INVALID_PARAM.getCode(), ex.getMessage());
            } else if (ex instanceof KnowledgeException knowledgeException) {
                log.warn("异常[KnowledgeException] ", ex);
                responseData = ReqResult.error(knowledgeException.getCode(), knowledgeException.getDisplayCode(),
                        ex.getMessage());
            } else if (ex instanceof SystemManagerException systemManagerException) {
                log.warn("异常[SystemManagerException] ", ex);
                responseData = ReqResult.error(systemManagerException.getCode(),
                        systemManagerException.getDisplayCode(), ex.getMessage());
            } else if (ex instanceof ComposeException composeException) {
                responseData = ReqResult.error(composeException.getCode(), composeException.getDisplayCode(),
                        composeException.getMessage());
            } else if (ex instanceof EcoMarketException ecoMarketException) {
                responseData = ReqResult.error(ecoMarketException.getCode(), ecoMarketException.getDisplayCode(),
                        ecoMarketException.getMessage());
            } else if (ex instanceof IllegalArgumentException) {
                responseData = ReqResult.error(ErrorCodeEnum.INVALID_PARAM.getCode(), ex.getMessage());
            } else if (ex instanceof NoResourceFoundException) {
                responseData = ReqResult.error(ErrorCodeEnum.API_NOT_FOUND.getCode(), ex.getMessage());
            } else {
                log.error("系统错误 ", ex);
                responseData = ReqResult.error(ErrorCodeEnum.SYS_ERROR.getCode(), "系统开小差啦，请稍后重试");
            }
        }
        return responseData;
    }
}