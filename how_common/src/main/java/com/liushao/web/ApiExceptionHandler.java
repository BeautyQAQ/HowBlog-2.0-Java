package com.liushao.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.liushao.entity.Result;
import com.liushao.entity.StatusCode;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatus status, WebRequest request) {
        String message = status.is5xxServerError() ? "服务暂时不可用，请稍后重试" : "请求格式或参数不正确";
        return new ResponseEntity<>(new Result(false, StatusCode.ERROR, message), headers, status);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Result> notFound(ResourceNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new Result(false, StatusCode.ERROR, "资源不存在"));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Result> forbidden(ForbiddenException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new Result(false, StatusCode.ACCESSERROR, "无权执行此操作"));
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<Result> invalidRequest(InvalidRequestException exception) {
        return ResponseEntity.badRequest().body(new Result(false, StatusCode.ERROR, "请求格式或参数不正确"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result> error(Exception exception) {
        LOGGER.error("Unhandled request failure: {}", exception.getClass().getName());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new Result(false, StatusCode.ERROR, "服务暂时不可用，请稍后重试"));
    }
}