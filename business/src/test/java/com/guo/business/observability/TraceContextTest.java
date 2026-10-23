package com.guo.business.observability;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * I18 链路标识：入口过滤器建立 traceId，作用域关闭后恢复原 MDC。
 */
class TraceContextTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void filterReusesIncomingTraceAndExposesRequestId() throws Exception {
        TraceIdFilter filter = new TraceIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/business/web/purchase/apply");
        request.addHeader(TraceContext.TRACE_HEADER, "trace-abc");
        request.addHeader(TraceContext.REQUEST_HEADER, "key-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> traceInChain = new AtomicReference<>();
        AtomicReference<String> requestInChain = new AtomicReference<>();
        filter.doFilter(request, response, (req, res) -> {
            traceInChain.set(MDC.get(TraceContext.TRACE_ID));
            requestInChain.set(MDC.get(TraceContext.REQUEST_ID));
        });

        assertEquals("trace-abc", traceInChain.get());
        assertEquals("key-123", requestInChain.get());
        assertEquals("trace-abc", response.getHeader(TraceContext.TRACE_HEADER));
        assertNull(MDC.get(TraceContext.TRACE_ID), "请求结束后应清理 traceId");
        assertNull(MDC.get(TraceContext.REQUEST_ID), "请求结束后应清理 requestId");
    }

    @Test
    void filterGeneratesTraceWhenHeaderMissing() throws Exception {
        TraceIdFilter filter = new TraceIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/business/internal/observability/metrics");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> traceInChain = new AtomicReference<>();
        filter.doFilter(request, response, (req, res) -> traceInChain.set(MDC.get(TraceContext.TRACE_ID)));

        assertNotNull(traceInChain.get());
        assertEquals(32, traceInChain.get().length());
        assertEquals(traceInChain.get(), response.getHeader(TraceContext.TRACE_HEADER));
    }

    @Test
    void scopeRestoresPreviousValues() {
        MDC.put(TraceContext.TRACE_ID, "outer");
        MDC.put(TraceContext.REQUEST_ID, "outer-request");

        try (TraceContext.Scope ignored = TraceContext.open("inner", "inner-request")) {
            assertEquals("inner", MDC.get(TraceContext.TRACE_ID));
            assertEquals("inner-request", MDC.get(TraceContext.REQUEST_ID));
        }

        assertEquals("outer", MDC.get(TraceContext.TRACE_ID));
        assertEquals("outer-request", MDC.get(TraceContext.REQUEST_ID));
    }
}
