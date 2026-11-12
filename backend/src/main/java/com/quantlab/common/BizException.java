package com.quantlab.common;

/**
 * 业务异常，携带对外可读的错误信息。
 */
public class BizException extends RuntimeException {

    private final int code;

    public BizException(String message) {
        this(400, message);
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
