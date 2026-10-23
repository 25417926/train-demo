package com.guo.business.alert;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * I18 应用内告警：不引入新监控平台，直接读取 Micrometer 指标并按规则判定。
 *
 * <p>规则与阈值（application.yaml {@code train.alerts}）：</p>
 * <ul>
 *     <li>死信数量 {@code mq_dlq_total} &gt; 0；</li>
 *     <li>消费积压 {@code mq_consume_lag} &gt; {@code mq-lag-threshold}；</li>
 *     <li>库存差异 {@code redis_inventory_drift} &gt; 0 持续 {@code drift-sustain-ms}（默认 3 分钟）。</li>
 * </ul>
 *
 * <p>同一规则/对象只保留一条 FIRING 记录，指标恢复后标记 RESOLVED；
 * 触发即输出 ERROR 日志，并可通过内部接口查询最近告警。</p>
 */
@Component
public class AlertService {

    public static final String RULE_DLQ = "MQ_DLQ_NOT_EMPTY";
    public static final String RULE_MQ_LAG = "MQ_CONSUME_LAG_HIGH";
    public static final String RULE_DRIFT = "REDIS_INVENTORY_DRIFT";
    public static final String LEVEL_CRITICAL = "CRITICAL";
    public static final String LEVEL_WARN = "WARN";

    private static final Logger LOG = LoggerFactory.getLogger(AlertService.class);
    private static final int HISTORY_LIMIT = 100;

    private final MeterRegistry meterRegistry;
    private final long lagThreshold;
    private final long driftSustainMs;

    /** 当前处于 FIRING 的告警，key = rule + ':' + target */
    private final Map<String, AlertEvent> firing = new LinkedHashMap<>();
    /** 差异指标首次大于 0 的时间，用于“持续 N 分钟”判定 */
    private final Map<String, Long> driftSince = new HashMap<>();
    private final Deque<AlertEvent> history = new ArrayDeque<>();
    private final AtomicLong sequence = new AtomicLong();

    public AlertService(MeterRegistry meterRegistry,
                        @Value("${train.alerts.mq-lag-threshold:100}") long lagThreshold,
                        @Value("${train.alerts.drift-sustain-ms:180000}") long driftSustainMs) {
        this.meterRegistry = meterRegistry;
        this.lagThreshold = lagThreshold;
        this.driftSustainMs = driftSustainMs;
    }

    @Scheduled(fixedDelayString = "${train.alerts.evaluate-ms:30000}",
            initialDelayString = "${train.alerts.initial-delay-ms:15000}")
    public void evaluate() {
        evaluateAt(System.currentTimeMillis());
    }

    /**
     * 按当前指标评估全部规则；测试可直接传入时间推进“持续时长”。
     */
    synchronized void evaluateAt(long now) {
        evaluateDlq(now);
        evaluateLag(now);
        evaluateDrift(now);
    }

    /**
     * 人工触发一条告警，验证告警日志与查询通道可用。
     */
    public synchronized AlertEvent triggerTest(String rule) {
        String name = rule == null || rule.trim().isEmpty() ? "MANUAL_TEST" : rule.trim();
        AlertEvent event = buildEvent(name, LEVEL_WARN, "manual", 1.0,
                "人工触发告警：用于验证告警记录与日志通道", now());
        history.addFirst(event);
        trimHistory();
        LOG.error("[ALERT] rule={} level={} target={} value={} message={}",
                event.getRule(), event.getLevel(), event.getTarget(), event.getValue(), event.getMessage());
        return event;
    }

    /**
     * 当前 FIRING 的告警。
     */
    public synchronized List<AlertEvent> activeAlerts() {
        return new ArrayList<>(firing.values());
    }

    /**
     * 最近告警（含已恢复），按触发时间倒序。
     */
    public synchronized List<AlertEvent> recentAlerts(int limit) {
        int size = Math.max(1, Math.min(limit, HISTORY_LIMIT));
        List<AlertEvent> result = new ArrayList<>(size);
        for (AlertEvent event : history) {
            if (result.size() >= size) {
                break;
            }
            result.add(event);
        }
        return result;
    }

    private void evaluateDlq(long now) {
        for (Map.Entry<String, Double> entry : gaugeValues("mq_dlq_total", "topic").entrySet()) {
            String key = RULE_DLQ + ":" + entry.getKey();
            if (entry.getValue() > 0) {
                fire(key, RULE_DLQ, LEVEL_CRITICAL, entry.getKey(), entry.getValue(),
                        "死信队列存在未处理消息 topic=" + entry.getKey(), now);
            } else {
                resolve(key, now, "死信已清空 topic=" + entry.getKey());
            }
        }
    }

    private void evaluateLag(long now) {
        for (Map.Entry<String, Double> entry : gaugeValues("mq_consume_lag", "topic").entrySet()) {
            String key = RULE_MQ_LAG + ":" + entry.getKey();
            if (entry.getValue() > lagThreshold) {
                fire(key, RULE_MQ_LAG, LEVEL_WARN, entry.getKey(), entry.getValue(),
                        "消费积压超过阈值 topic=" + entry.getKey() + " lag=" + entry.getValue()
                                + " threshold=" + lagThreshold, now);
            } else {
                resolve(key, now, "消费积压已回落 topic=" + entry.getKey());
            }
        }
    }

    private void evaluateDrift(long now) {
        long sustain = Math.max(1L, driftSustainMs);
        for (Map.Entry<String, Double> entry : gaugeValues("redis_inventory_drift", "scope").entrySet()) {
            String key = RULE_DRIFT + ":" + entry.getKey();
            if (entry.getValue() <= 0) {
                driftSince.remove(key);
                resolve(key, now, "库存差异已归零 scope=" + entry.getKey());
                continue;
            }
            Long since = driftSince.get(key);
            if (since == null) {
                driftSince.put(key, now);
                continue;
            }
            if (now - since >= sustain) {
                fire(key, RULE_DRIFT, LEVEL_WARN, entry.getKey(), entry.getValue(),
                        "库存差异持续存在 scope=" + entry.getKey() + " drift=" + entry.getValue()
                                + " since=" + since, now);
            }
        }
    }

    private void fire(String key, String rule, String level, String target, double value,
                      String message, long now) {
        if (firing.containsKey(key)) {
            return;
        }
        AlertEvent event = buildEvent(rule, level, target, value, message, now);
        firing.put(key, event);
        history.addFirst(event);
        trimHistory();
        LOG.error("[ALERT] rule={} level={} target={} value={} message={}",
                rule, level, target, value, message);
    }

    private void resolve(String key, long now, String message) {
        AlertEvent event = firing.remove(key);
        if (event == null) {
            return;
        }
        event.setStatus(AlertEvent.STATUS_RESOLVED);
        event.setRecoveredAt(new Date(now));
        LOG.warn("[ALERT-RESOLVED] rule={} target={} message={}", event.getRule(), event.getTarget(), message);
    }

    private AlertEvent buildEvent(String rule, String level, String target, double value,
                                  String message, long now) {
        AlertEvent event = new AlertEvent();
        event.setId(rule + "-" + sequence.incrementAndGet());
        event.setRule(rule);
        event.setLevel(level);
        event.setTarget(target);
        event.setValue(value);
        event.setMessage(message);
        event.setTriggeredAt(new Date(now));
        event.setStatus(AlertEvent.STATUS_FIRING);
        return event;
    }

    private void trimHistory() {
        while (history.size() > HISTORY_LIMIT) {
            history.removeLast();
        }
    }

    /**
     * 读取指定指标的全部 tag 维度值，tagName 即维度名（topic/scope）。
     */
    private Map<String, Double> gaugeValues(String metric, String tagName) {
        Map<String, Double> values = new LinkedHashMap<>();
        for (Gauge gauge : meterRegistry.find(metric).gauges()) {
            String target = gauge.getId().getTag(tagName);
            values.put(target == null ? metric : target, gauge.value());
        }
        return values;
    }

    private static long now() {
        return System.currentTimeMillis();
    }
}
