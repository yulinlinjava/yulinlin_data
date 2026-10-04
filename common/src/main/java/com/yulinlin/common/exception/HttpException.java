package com.yulinlin.common.exception;

public class HttpException extends ApiException {
    public HttpException(String message, int code) {
        super(message, code);
    }

    public HttpException(String message, Throwable cause, int code) {
        super(message, cause, code);
    }
}
