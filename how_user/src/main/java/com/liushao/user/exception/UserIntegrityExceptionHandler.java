package com.liushao.user.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.liushao.entity.Result;
import com.liushao.entity.StatusCode;

/**
 * 手机号唯一约束（uk_tb_user_mobile）的兜底处理：应用层查重与数据库约束之间的并发窗口
 * 由约束兜底，冲突按“手机号已注册”返回，而不是 500；其它完整性异常保持脱敏 500。
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UserIntegrityExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserIntegrityExceptionHandler.class);

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Result> dataIntegrity(DataIntegrityViolationException exception) {
        String cause = exception.getMostSpecificCause().getMessage();
        if (cause != null && cause.contains("uk_tb_user_mobile")) {
            return ResponseEntity.ok(new Result(false, StatusCode.REPERROR, "手机号已注册"));
        }
        LOGGER.error("Unhandled data integrity failure: {}", exception.getClass().getName());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new Result(false, StatusCode.ERROR, "服务暂时不可用，请稍后重试"));
    }
}
