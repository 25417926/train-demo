package com.guo.business.observability;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * I18 链路标识：traceId / requestId 在 HTTP 入口、MQ 消息与消费端之间透传。
 *
 * <p>HTTP 入口由 {@link TraceIdFilter} 建立，MQ 生产端写入消息头 {@code TRACE_ID}、
 * {@code REQUEST_ID}，消费端通过 {@link #open(String, String)} 恢复到 MDC。</p>
 */
public final class TraceContext {

    /** MDC 中的链路标识 */
    public static final String TRACE_ID = "traceId";
    /** MDC 中的请求标识，与 Idempotency-Key 一致 */
    public static final String REQUEST_ID = "requestId";

    /** HTTP 请求/响应头 */
    public static final String TRACE_HEADER = "X-Trace-Id";
    public static final String REQUEST_HEADER = "Idempotency-Key";

    /** MQ 消息头 */
    public static final String MQ_TRACE_HEADER = "TRACE_ID";
    public static final String MQ_REQUEST_HEADER = "REQUEST_ID";

    private TraceContext() {
    }

    /**
     * 当前线程的 traceId，未建立时返回 null。
     */
    public static String currentTraceId() {
        String traceId = MDC.get(TRACE_ID);
        return isBlank(traceId) ? null : traceId;
    }

    /**
     * 当前 traceId，不存在时生成新的（不写入 MDC）。
     */
    public static String traceIdOrNew() {
        String traceId = currentTraceId();
        return traceId == null ? newTraceId() : traceId;
    }

    public static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 以指定 traceId/requestId 打开一个日志上下文，close 时恢复原值。
     *
     * <p>traceId 为空时沿用当前值；两者都为空则等价于不改变上下文。</p>
     */
    public static Scope open(String traceId, String requestId) {
        String previousTrace = MDC.get(TRACE_ID);
        String previousRequest = MDC.get(REQUEST_ID);
        if (!isBlank(traceId)) {
            MDC.put(TRACE_ID, traceId);
        }
        if (!isBlank(requestId)) {
            MDC.put(REQUEST_ID, requestId);
        }
        return new Scope(previousTrace, previousRequest);
    }

    /**
     * 定时任务/后台线程使用：没有 traceId 时补一个，close 后清理。
     */
    public static Scope openIfAbsent() {
        return open(traceIdOrNew(), null);
    }

    /**
     * 在已有上下文上补充 requestId，关闭时恢复。
     */
    public static Scope withRequestId(String requestId) {
        return open(currentTraceId(), requestId);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * 上下文作用域：支持 try-with-resources，保证线程池复用时不残留 MDC。
     */
    public static final class Scope implements AutoCloseable {

        private final String previousTrace;
        private final String previousRequest;

        private Scope(String previousTrace, String previousRequest) {
            this.previousTrace = previousTrace;
            this.previousRequest = previousRequest;
        }

        @Override
        public void close() {
            restore(TRACE_ID, previousTrace);
            restore(REQUEST_ID, previousRequest);
        }

        private static void restore(String key, String previous) {
            if (previous == null) {
                MDC.remove(key);
            } else {
                MDC.put(key, previous);
            }
        }
    }
}
