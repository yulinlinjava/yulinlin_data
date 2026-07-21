package com.yulinlin.common.exception;

public class ApiException extends RuntimeException {
    private int code;

    public ApiException(String message, int code) {
        super(message);
        this.code = code;
    }

    public ApiException(String message, Throwable cause, int code) {
        super(message, cause);
        this.code = code;
    }

    public int getCode() {
        return this.code;
    }
}
