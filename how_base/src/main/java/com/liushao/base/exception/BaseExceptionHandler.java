package com.liushao.base.exception;

import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.liushao.web.ApiExceptionHandler;

/**
 * 全局异常处理类(通知类)
 * // @ControllerAdvice
 * 组合注解，相当于@ControllerAdvice+@ResponseBody
 * @author SZ-UserBDG7
 */
@RestControllerAdvice
public class BaseExceptionHandler extends ApiExceptionHandler {
}
