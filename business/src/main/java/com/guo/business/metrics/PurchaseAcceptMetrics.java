package com.guo.business.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.TimeUnit;

/**
 * I18 受理链路指标：{@code purchase_accept_total{result}} 与 {@code purchase_accept_latency}。
 *
 * <p>success / duplicate / rejected 三类分别计数，rejected 额外带 reason 标签（错误码）。
 * Timer 供 Actuator 查看均值/最大值；另保留最近 {@value #SAMPLE_CAPACITY} 次耗时样本，
 * 用于不依赖额外直方图组件计算 P50/P95/P99。</p>
 */
@Component
public class PurchaseAcceptMetrics {

    static final int SAMPLE_CAPACITY = 1024;

    private final MeterRegistry meterRegistry;
    private final Timer latency;
    private final Deque<Double> samples = new ArrayDeque<>(SAMPLE_CAPACITY);

    public PurchaseAcceptMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.latency = Timer.builder("purchase_accept_latency")
                .description("购票受理耗时")
                .register(meterRegistry);
    }

    /**
     * 受理成功（包含最终创建订单与消息已确认）。
     */
    public void recordSuccess(long startNanos) {
        meterRegistry.counter("purchase_accept_total", "result", "success").increment();
        recordLatency(startNanos);
    }

    /**
     * 幂等命中：同一 Idempotency-Key 已受理，直接返回已有订单。
     */
    public void recordDuplicate(long startNanos) {
        meterRegistry.counter("purchase_accept_total", "result", "duplicate").increment();
        recordLatency(startNanos);
    }

    /**
     * 受理被拒绝，reason 使用 {@code PurchaseErrorCode} 名称。
     */
    public void recordRejected(String reason, long startNanos) {
        String tag = reason == null || reason.trim().isEmpty() ? "UNKNOWN" : reason;
        meterRegistry.counter("purchase_accept_total", "result", "rejected", "reason", tag).increment();
        recordLatency(startNanos);
    }

    /**
     * 最近样本的分位耗时（毫秒），样本为空时返回 0。
     */
    public synchronized double percentileMs(double percentile) {
        if (samples.isEmpty()) {
            return 0.0;
        }
        double[] values = samples.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        double ratio = Math.min(1.0, Math.max(0.0, percentile / 100.0));
        int index = (int) Math.ceil(ratio * values.length) - 1;
        return values[Math.max(0, Math.min(index, values.length - 1))];
    }

    private void recordLatency(long startNanos) {
        long nanos = System.nanoTime() - startNanos;
        latency.record(nanos, TimeUnit.NANOSECONDS);
        synchronized (this) {
            if (samples.size() >= SAMPLE_CAPACITY) {
                samples.removeFirst();
            }
            samples.addLast(nanos / 1_000_000.0);
        }
    }
}
