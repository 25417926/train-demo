package com.guo.business.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.rocketmq.client.consumer.DefaultMQPullConsumer;
import org.apache.rocketmq.common.message.MessageQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * I18 死信指标：{@code mq_dlq_total{topic}}。
 *
 * <p>每分钟用 Pull 消费者读取死信队列的 min/max 位点，差值即当前死信数量。
 * 死信主题不存在或 Broker 不可用时保留上一次观测值，不影响主流程。</p>
 */
@Component
public class DlqMetrics {

    private static final Logger LOG = LoggerFactory.getLogger(DlqMetrics.class);

    private final String nameServer;
    private final Map<String, AtomicLong> countByTopic = new LinkedHashMap<>();

    public DlqMetrics(@Value("${rocketmq.name-server}") String nameServer,
                      @Value("${train.metrics.dlq-topics:}") String topics,
                      MeterRegistry meterRegistry) {
        this.nameServer = nameServer;
        for (String topic : topics.split(",")) {
            String name = topic.trim();
            if (name.isEmpty() || countByTopic.containsKey(name)) {
                continue;
            }
            AtomicLong count = new AtomicLong();
            countByTopic.put(name, count);
            Gauge.builder("mq_dlq_total", count, AtomicLong::doubleValue)
                    .tag("topic", name)
                    .register(meterRegistry);
        }
        LOG.info("死信数量采集已注册 topics={}", countByTopic.keySet());
    }

    @Scheduled(fixedDelayString = "${train.metrics.dlq-refresh-ms:60000}",
            initialDelayString = "${train.metrics.dlq-initial-delay-ms:60000}")
    public void refresh() {
        for (Map.Entry<String, AtomicLong> entry : countByTopic.entrySet()) {
            try {
                entry.getValue().set(count(entry.getKey()));
            } catch (Exception e) {
                LOG.debug("死信数量采集失败 topic={}：{}", entry.getKey(), e.getMessage());
            }
        }
    }

    /**
     * 单个死信主题的消息总数（各队列 maxOffset - minOffset 之和）。
     */
    private long count(String topic) throws Exception {
        DefaultMQPullConsumer consumer = new DefaultMQPullConsumer("business-dlq-metrics");
        consumer.setNamesrvAddr(nameServer);
        try {
            consumer.start();
            Set<MessageQueue> queues = consumer.fetchSubscribeMessageQueues(topic);
            long total = 0L;
            for (MessageQueue queue : queues) {
                total += Math.max(0L, consumer.maxOffset(queue) - consumer.minOffset(queue));
            }
            return total;
        } finally {
            consumer.shutdown();
        }
    }
}
