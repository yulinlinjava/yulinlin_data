package com.yulinlin.starter.web;

import com.yulinlin.common.exception.ApiException;
import com.yulinlin.starter.domain.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/** Default JSON error envelope. Applications can replace this bean with their own advice. */
@Slf4j
@Order(Ordered.LOWEST_PRECEDENCE)
@RestControllerAdvice
public class StarterExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<R<Void>> handleApiException(ApiException error) {
        HttpStatus status = statusFor(error.getCode());
        return response(status, status.value(), message(error.getMessage(), "请求处理失败"));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<R<Void>> handleBindException(BindException error) {
        FieldError fieldError = error.getBindingResult().getFieldError();
        String detail = fieldError == null
                ? "请求参数校验失败"
                : message(fieldError.getDefaultMessage(), fieldError.getField() + "参数不正确");
        return response(HttpStatus.BAD_REQUEST, HttpStatus.BAD_REQUEST.value(), detail);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, ServletRequestBindingException.class})
    public ResponseEntity<R<Void>> handleBadRequest(Exception error) {
        return response(HttpStatus.BAD_REQUEST, HttpStatus.BAD_REQUEST.value(), "请求参数格式不正确");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<R<Void>> handleIllegalArgument(IllegalArgumentException error) {
        return response(HttpStatus.BAD_REQUEST, HttpStatus.BAD_REQUEST.value(),
                message(error.getMessage(), "请求参数不正确"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<R<Void>> handleResponseStatus(ResponseStatusException error) {
        HttpStatusCode status = error.getStatusCode();
        return response(status, status.value(), message(error.getReason(), "请求处理失败"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<Void>> handleUnexpected(Exception error) {
        log.error("控制器发生未处理异常", error);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "系统繁忙，请稍后重试");
    }

    private ResponseEntity<R<Void>> response(HttpStatusCode status, int code, String message) {
        return ResponseEntity.status(status).body(R.fail(code, message));
    }

    private HttpStatus statusFor(int code) {
        HttpStatus status = HttpStatus.resolve(code);
        return status != null && status.isError() ? status : HttpStatus.BAD_REQUEST;
    }

    private String message(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
