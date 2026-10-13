package com.liushao.web;

import java.io.IOException;
import java.util.UUID;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 为每个请求分配关联 ID，写入 MDC 供日志输出，并在响应头回传。
 * 客户端可以通过 {@link #HEADER} 传入自己的关联 ID，但只接受受限字符集的短值，避免日志注入。
 */
public class RequestCorrelationFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private static final int MAX_LENGTH = 64;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = resolve(request.getHeader(HEADER));
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    static String resolve(String provided) {
        if (provided != null && !provided.isBlank() && provided.length() <= MAX_LENGTH && isSafe(provided)) {
            return provided;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static boolean isSafe(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            boolean allowed = (character >= 'a' && character <= 'z')
                    || (character >= 'A' && character <= 'Z')
                    || (character >= '0' && character <= '9')
                    || character == '-' || character == '_' || character == '.';
            if (!allowed) {
                return false;
            }
        }
        return true;
    }
}