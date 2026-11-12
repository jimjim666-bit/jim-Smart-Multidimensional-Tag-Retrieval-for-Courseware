package com.taglib.common.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.taglib.common.filter.RequestIdFilter;
import lombok.Data;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Data
public class Result<T> {
    private String code;
    private String message;
    private T data;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String requestId;

    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.setCode("OK");
        r.setMessage("操作成功");
        r.setData(data);
        r.setRequestId(currentRequestId());
        return r;
    }

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> error(String code, String message) {
        return error(code, message, null);
    }

    public static <T> Result<T> error(String code, String message, T data) {
        Result<T> r = new Result<>();
        r.setCode(code);
        r.setMessage(message);
        r.setData(data);
        r.setRequestId(currentRequestId());
        return r;
    }

    /** 非 HTTP 上下文（如定时任务）返回 null，序列化时省略该字段 */
    private static String currentRequestId() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes) {
            Object requestId = ((ServletRequestAttributes) attrs).getRequest().getAttribute(RequestIdFilter.ATTRIBUTE);
            return requestId == null ? null : requestId.toString();
        }
        return null;
    }
}
