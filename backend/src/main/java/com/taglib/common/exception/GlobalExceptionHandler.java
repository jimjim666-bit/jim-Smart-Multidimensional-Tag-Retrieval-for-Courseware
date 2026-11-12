package com.taglib.common.exception;

import com.taglib.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Result<?> handleBiz(BizException e) {
        log.warn("业务异常: {} - {}", e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage(), e.getData());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<?> handleBodyReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return Result.error("PARAM_ERROR", "请求参数格式有误");
    }

    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class
    })
    public Result<?> handleParam(Exception e) {
        log.warn("请求参数错误: {}", e.getMessage());
        return Result.error("PARAM_ERROR", "请求参数格式有误");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<?> handleUploadTooLarge(MaxUploadSizeExceededException e) {
        log.warn("上传文件超限: {}", e.getMessage());
        return Result.error("RESOURCE_FILE_TOO_LARGE", "文件大小超出限制");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public Result<?> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("请求方法不支持: {}", e.getMessage());
        return Result.error("METHOD_NOT_ALLOWED", "请求方法不支持");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<?> handleOther(Exception e) {
        log.error("服务器内部错误", e);
        return Result.error("SYSTEM_ERROR", "服务器开小差了，请稍后重试");
    }
}
