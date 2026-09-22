package com.liushao.user.exception;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.liushao.entity.Result;
import com.liushao.entity.StatusCode;
import com.liushao.user.service.AuthRateLimiter;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthRateLimitExceptionHandler {
    @ExceptionHandler(AuthRateLimiter.Rejected.class)
    public ResponseEntity<Result> rejected(AuthRateLimiter.Rejected exception) {
        boolean limited = exception.getRetryAfterSeconds() > 0;
        return ResponseEntity.status(limited ? 429 : 503).header("Cache-Control", "no-store")
                .header("Retry-After", String.valueOf(limited ? exception.getRetryAfterSeconds() : 5))
                .body(new Result(false, StatusCode.ERROR, limited
                        ? "\u8bf7\u6c42\u8fc7\u4e8e\u9891\u7e41\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5"
                        : "\u670d\u52a1\u6682\u65f6\u4e0d\u53ef\u7528\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5"));
    }
}