package com.liushao.web;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * 注册请求关联 ID 过滤器。三个业务服务的启动类通过 {@code @Import} 引入。
 */
@Configuration
public class WebObservabilityConfiguration {

    @Bean
    public FilterRegistrationBean<RequestCorrelationFilter> requestCorrelationFilter() {
        FilterRegistrationBean<RequestCorrelationFilter> registration =
                new FilterRegistrationBean<>(new RequestCorrelationFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}