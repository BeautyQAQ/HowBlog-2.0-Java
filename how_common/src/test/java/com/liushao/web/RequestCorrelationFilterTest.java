package com.liushao.web;

import java.util.concurrent.atomic.AtomicReference;

import javax.servlet.FilterChain;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class RequestCorrelationFilterTest {
    private final RequestCorrelationFilter filter = new RequestCorrelationFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void generatesCorrelationIdWhenHeaderIsMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/label");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> duringChain = new AtomicReference<>();
        FilterChain chain = (servletRequest, servletResponse) ->
                duringChain.set(MDC.get(RequestCorrelationFilter.MDC_KEY));

        filter.doFilter(request, response, chain);

        String requestId = response.getHeader(RequestCorrelationFilter.HEADER);
        assertNotNull(requestId);
        assertFalse(requestId.isBlank());
        assertEquals(requestId, duringChain.get(), "日志上下文必须使用同一个关联 ID");
        assertNull(MDC.get(RequestCorrelationFilter.MDC_KEY), "请求结束后必须清理 MDC");
    }

    @Test
    void keepsSafeProvidedCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/article");
        request.addHeader(RequestCorrelationFilter.HEADER, "trace-1234.ab_CD");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> { });

        assertEquals("trace-1234.ab_CD", response.getHeader(RequestCorrelationFilter.HEADER));
    }

    @Test
    void replacesProvidedCorrelationIdWithUnsafeCharacters() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/article");
        request.addHeader(RequestCorrelationFilter.HEADER, "trace 1234\r\nforged-entry");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> { });

        String requestId = response.getHeader(RequestCorrelationFilter.HEADER);
        assertNotNull(requestId);
        assertNotEquals("trace 1234\r\nforged-entry", requestId);
        assertFalse(requestId.contains("\r"));
        assertFalse(requestId.contains("\n"));
    }

    @Test
    void replacesProvidedCorrelationIdThatIsBlankOrTooLong() throws Exception {
        MockHttpServletResponse blankResponse = new MockHttpServletResponse();
        MockHttpServletRequest blankRequest = new MockHttpServletRequest("GET", "/article");
        blankRequest.addHeader(RequestCorrelationFilter.HEADER, "   ");

        filter.doFilter(blankRequest, blankResponse, (servletRequest, servletResponse) -> { });

        assertFalse(blankResponse.getHeader(RequestCorrelationFilter.HEADER).isBlank());

        String tooLong = "a".repeat(65);
        MockHttpServletResponse longResponse = new MockHttpServletResponse();
        MockHttpServletRequest longRequest = new MockHttpServletRequest("GET", "/article");
        longRequest.addHeader(RequestCorrelationFilter.HEADER, tooLong);

        filter.doFilter(longRequest, longResponse, (servletRequest, servletResponse) -> { });

        assertNotEquals(tooLong, longResponse.getHeader(RequestCorrelationFilter.HEADER));
    }
}