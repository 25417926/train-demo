package com.guo.business.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * I18 可观测面板数据：从 Micrometer 注册表汇总关键指标快照。
 *
 * <p>不引入 Prometheus/Grafana，Actuator {@code /actuator/metrics} 之外再提供一份聚合视图，
 * 便于运维与联调在一个接口里看到受理量、成功率、P99、积压、死信、对账差异与 Lua 耗时。</p>
 */
@Service
public class ObservabilitySnapshotService {

    private final MeterRegistry meterRegistry;
    private final PurchaseAcceptMetrics purchaseAcceptMetrics;

    public ObservabilitySnapshotService(MeterRegistry meterRegistry,
                                        PurchaseAcceptMetrics purchaseAcceptMetrics) {
        this.meterRegistry = meterRegistry;
        this.purchaseAcceptMetrics = purchaseAcceptMetrics;
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("purchaseAccept", purchaseAccept());
        snapshot.put("mqConsumeLag", gaugeValues("mq_consume_lag", "topic"));
        snapshot.put("mqDlqTotal", gaugeValues("mq_dlq_total", "topic"));
        snapshot.put("inventoryDrift", gaugeValues("redis_inventory_drift", "scope"));
        snapshot.put("luaDurationMs", distributionValues("lua_duration_ms", "script"));
        snapshot.put("reconcileFixTotal", counterValues("reconcile_fix_total", null));
        return snapshot;
    }

    /**
     * 受理量、成功率与延迟分位（success 与 rejected 计入成功率，duplicate 为幂等命中）。
     */
    private Map<String, Object> purchaseAccept() {
        long success = 0L;
        long duplicate = 0L;
        long rejected = 0L;
        Map<String, Double> rejectedByReason = new LinkedHashMap<>();
        for (Counter counter : meterRegistry.find("purchase_accept_total").counters()) {
            String result = counter.getId().getTag("result");
            long count = Math.round(counter.count());
            if ("success".equals(result)) {
                success += count;
            } else if ("duplicate".equals(result)) {
                duplicate += count;
            } else if ("rejected".equals(result)) {
                rejected += count;
                String reason = counter.getId().getTag("reason");
                rejectedByReason.merge(reason == null ? "UNKNOWN" : reason, counter.count(), Double::sum);
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("success", success);
        summary.put("duplicate", duplicate);
        summary.put("rejected", rejected);
        summary.put("rejectedByReason", rejectedByReason);
        long judged = success + rejected;
        summary.put("successRate", judged == 0L ? 1.0 : Math.round(success * 10000.0 / judged) / 10000.0);
        summary.put("latency", latency());
        return summary;
    }

    private Map<String, Object> latency() {
        Map<String, Object> latency = new LinkedHashMap<>();
        Timer timer = meterRegistry.find("purchase_accept_latency").timer();
        if (timer == null) {
            return latency;
        }
        latency.put("count", timer.count());
        latency.put("meanMs", round(timer.mean(TimeUnit.MILLISECONDS)));
        latency.put("maxMs", round(timer.max(TimeUnit.MILLISECONDS)));
        latency.put("p50Ms", round(purchaseAcceptMetrics.percentileMs(50)));
        latency.put("p95Ms", round(purchaseAcceptMetrics.percentileMs(95)));
        latency.put("p99Ms", round(purchaseAcceptMetrics.percentileMs(99)));
        return latency;
    }

    private Map<String, Double> gaugeValues(String metric, String tagName) {
        Map<String, Double> values = new LinkedHashMap<>();
        for (Gauge gauge : meterRegistry.find(metric).gauges()) {
            values.put(tagOf(gauge.getId().getTag(tagName), metric), round(gauge.value()));
        }
        return values;
    }

    private Map<String, Double> counterValues(String metric, String tagName) {
        Map<String, Double> values = new LinkedHashMap<>();
        for (Counter counter : meterRegistry.find(metric).counters()) {
            values.put(tagOf(counter.getId().getTag(tagName), metric), counter.count());
        }
        return values;
    }

    private Map<String, Map<String, Object>> distributionValues(String metric, String tagName) {
        Map<String, Map<String, Object>> values = new LinkedHashMap<>();
        for (DistributionSummary summary : meterRegistry.find(metric).summaries()) {
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("count", summary.count());
            stats.put("totalMs", round(summary.totalAmount()));
            stats.put("meanMs", round(summary.mean()));
            stats.put("maxMs", round(summary.max()));
            values.put(tagOf(summary.getId().getTag(tagName), metric), stats);
        }
        return values;
    }

    private static String tagOf(String tag, String fallback) {
        return tag == null || tag.isEmpty() ? fallback : tag;
    }

    private static double round(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return Math.round(value * 100.0) / 100.0;
    }
}
