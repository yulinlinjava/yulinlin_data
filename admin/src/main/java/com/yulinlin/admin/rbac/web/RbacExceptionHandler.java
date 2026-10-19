package com.yulinlin.admin.rbac.web;

import com.yulinlin.security.auth.LoginException;
import com.yulinlin.starter.domain.R;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 只处理业务登录拒绝，基础设施异常继续交给 StarterExceptionHandler。 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackageClasses = AuthController.class)
public class RbacExceptionHandler {
    @ExceptionHandler(LoginException.class)
    public ResponseEntity<R<Void>> loginRejected(LoginException error) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(R.fail(401, error.getMessage()));
    }
}
