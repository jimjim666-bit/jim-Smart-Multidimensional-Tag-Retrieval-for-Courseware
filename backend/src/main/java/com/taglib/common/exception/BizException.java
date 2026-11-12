package com.taglib.common.exception;

import lombok.Getter;

@Getter
public class BizException extends RuntimeException {
    private final String code;
    /** 随错误码返回的附加数据，例如 AUTH_ACCOUNT_LOCKED 的 lockedUntil */
    private final Object data;

    public BizException(String code, String message) {
        this(code, message, null);
    }

    public BizException(String code, String message, Object data) {
        super(message);
        this.code = code;
        this.data = data;
    }
}
