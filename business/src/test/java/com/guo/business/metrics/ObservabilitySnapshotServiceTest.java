package com.guo.business.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * I18 面板快照：受理成功率、P99、积压与死信等指标能被聚合读取。
 */
class ObservabilitySnapshotServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void snapshotAggregatesAcceptAndMqMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PurchaseAcceptMetrics metrics = new PurchaseAcceptMetrics(registry);
        metrics.recordSuccess(System.nanoTime() - 20_000_000L);
        metrics.recordRejected("TICKET_NOT_ENOUGH", System.nanoTime() - 2_000_000L);

        AtomicLong lag = new AtomicLong(7L);
        Gauge.builder("mq_consume_lag", lag, AtomicLong::doubleValue)
                .tag("topic", "order-confirm").register(registry);
        AtomicLong dlq = new AtomicLong(1L);
        Gauge.builder("mq_dlq_total", dlq, AtomicLong::doubleValue)
                .tag("topic", "%DLQ%business-order-confirm").register(registry);

        ObservabilitySnapshotService service = new ObservabilitySnapshotService(registry, metrics);
        Map<String, Object> snapshot = service.snapshot();

        Map<String, Object> accept = (Map<String, Object>) snapshot.get("purchaseAccept");
        assertEquals(1L, accept.get("success"));
        assertEquals(0L, accept.get("duplicate"));
        assertEquals(1L, accept.get("rejected"));
        assertEquals(0.5, (Double) accept.get("successRate"), 0.0001);

        Map<String, Object> rejected = (Map<String, Object>) accept.get("rejectedByReason");
        assertEquals(1.0, rejected.get("TICKET_NOT_ENOUGH"));

        Map<String, Object> latency = (Map<String, Object>) accept.get("latency");
        assertEquals(2L, latency.get("count"));
        assertTrue(((Double) latency.get("p99Ms")) >= 10.0, "P99 应覆盖较长的一次耗时");

        Map<String, Object> lagValues = (Map<String, Object>) snapshot.get("mqConsumeLag");
        assertEquals(7.0, lagValues.get("order-confirm"));
        Map<String, Object> dlqValues = (Map<String, Object>) snapshot.get("mqDlqTotal");
        assertEquals(1.0, dlqValues.get("%DLQ%business-order-confirm"));
    }
}
