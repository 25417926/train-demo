package com.guo.business.observability;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * I18 入口过滤器：为每个 HTTP 请求建立 traceId，并透传 Idempotency-Key 作为 requestId。
 *
 * <p>上游已带 {@code X-Trace-Id} 时沿用，保证网关/调用方链路不断裂；否则生成 32 位十六进制。
 * 响应同样返回该头，便于前端排查。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = request.getHeader(TraceContext.TRACE_HEADER);
        if (traceId == null || traceId.trim().isEmpty()) {
            traceId = TraceContext.newTraceId();
        }
        String requestId = request.getHeader(TraceContext.REQUEST_HEADER);
        try (TraceContext.Scope ignored = TraceContext.open(traceId, requestId)) {
            response.setHeader(TraceContext.TRACE_HEADER, TraceContext.currentTraceId());
            filterChain.doFilter(request, response);
        }
    }
}
