package com.guo.business.alert;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * I18 告警规则：DLQ>0、积压超阈值、库存差异持续 3 分钟，以及恢复与人工自测。
 */
class AlertServiceTest {

    private static final String DLQ_TOPIC = "%DLQ%business-order-confirm";
    private static final String LAG_TOPIC = "order-confirm";
    private static final String DRIFT_SCOPE = "ticket";
    private static final long LAG_THRESHOLD = 100L;
    private static final long DRIFT_SUSTAIN_MS = 180_000L;

    private SimpleMeterRegistry registry;
    private AlertService alertService;
    private AtomicLong dlq;
    private AtomicLong lag;
    private AtomicLong drift;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        dlq = new AtomicLong();
        lag = new AtomicLong();
        drift = new AtomicLong();
        Gauge.builder("mq_dlq_total", dlq, AtomicLong::doubleValue).tag("topic", DLQ_TOPIC).register(registry);
        Gauge.builder("mq_consume_lag", lag, AtomicLong::doubleValue).tag("topic", LAG_TOPIC).register(registry);
        Gauge.builder("redis_inventory_drift", drift, AtomicLong::doubleValue).tag("scope", DRIFT_SCOPE).register(registry);
        alertService = new AlertService(registry, LAG_THRESHOLD, DRIFT_SUSTAIN_MS);
    }

    @AfterEach
    void tearDown() {
        registry.close();
    }

    @Test
    void dlqFiresOnceAndResolves() {
        dlq.set(2L);
        alertService.evaluateAt(1_000L);
        assertEquals(1, alertService.activeAlerts().size());
        assertEquals(AlertService.RULE_DLQ, alertService.activeAlerts().get(0).getRule());

        // 同一状态重复评估不重复触发
        alertService.evaluateAt(31_000L);
        assertEquals(1, alertService.activeAlerts().size());

        dlq.set(0L);
        alertService.evaluateAt(61_000L);
        assertTrue(alertService.activeAlerts().isEmpty());

        List<AlertEvent> recent = alertService.recentAlerts(10);
        assertFalse(recent.isEmpty());
        assertEquals(AlertEvent.STATUS_RESOLVED, recent.get(0).getStatus());
    }

    @Test
    void lagFiresOnlyAboveThreshold() {
        lag.set(LAG_THRESHOLD);
        alertService.evaluateAt(1_000L);
        assertTrue(alertService.activeAlerts().isEmpty(), "等于阈值不告警");

        lag.set(LAG_THRESHOLD + 1);
        alertService.evaluateAt(2_000L);
        assertEquals(1, alertService.activeAlerts().size());
        assertEquals(AlertService.RULE_MQ_LAG, alertService.activeAlerts().get(0).getRule());
    }

    @Test
    void driftFiresAfterSustainedThreeMinutes() {
        drift.set(3L);
        alertService.evaluateAt(0L);
        assertTrue(alertService.activeAlerts().isEmpty(), "首次发现差异不立即告警");

        alertService.evaluateAt(DRIFT_SUSTAIN_MS - 1);
        assertTrue(alertService.activeAlerts().isEmpty(), "未满 3 分钟不告警");

        alertService.evaluateAt(DRIFT_SUSTAIN_MS);
        assertEquals(1, alertService.activeAlerts().size());
        assertEquals(AlertService.RULE_DRIFT, alertService.activeAlerts().get(0).getRule());
    }

    @Test
    void driftTimerResetsWhenRecovered() {
        drift.set(3L);
        alertService.evaluateAt(0L);
        drift.set(0L);
        alertService.evaluateAt(100_000L);

        drift.set(3L);
        alertService.evaluateAt(150_000L);
        alertService.evaluateAt(320_000L);
        assertTrue(alertService.activeAlerts().isEmpty(), "差异归零后重新计时");

        alertService.evaluateAt(330_000L);
        assertEquals(1, alertService.activeAlerts().size());
    }

    @Test
    void manualTestAlertIsQueryable() {
        AlertEvent event = alertService.triggerTest(null);
        assertEquals(AlertEvent.STATUS_FIRING, event.getStatus());
        assertEquals("MANUAL_TEST", event.getRule());
        assertEquals(1, alertService.recentAlerts(10).size());
    }
}
