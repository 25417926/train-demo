package com.guo.business.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.impl.MQClientAPIImpl;
import org.apache.rocketmq.client.impl.factory.MQClientInstance;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.constant.PermName;
import org.apache.rocketmq.common.protocol.ResponseCode;
import org.apache.rocketmq.common.protocol.header.QueryConsumerOffsetRequestHeader;
import org.apache.rocketmq.common.protocol.route.BrokerData;
import org.apache.rocketmq.common.protocol.route.QueueData;
import org.apache.rocketmq.common.protocol.route.TopicRouteData;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * I11 消费积压指标：{@code mq_consume_lag{topic}}。
 *
 * <p>复用当前应用的 RocketMQ 客户端连接，从 Broker 读取每个队列的最大位点与消费组提交位点，
 * 差值之和即积压。Broker/NameServer 不可用时跳过本轮，保留上一次观测值，不影响主流程。</p>
 */
@Component
public class MqConsumeLagMetrics {

    private static final Logger LOG = LoggerFactory.getLogger(MqConsumeLagMetrics.class);

    private static final long TIMEOUT_MS = 3000L;

    private final RocketMQTemplate rocketMQTemplate;
    private final Map<String, AtomicLong> lagByTopic = new LinkedHashMap<>();
    private final Map<String, String> groupByTopic = new LinkedHashMap<>();

    public MqConsumeLagMetrics(RocketMQTemplate rocketMQTemplate,
                               MeterRegistry meterRegistry,
                               @Value("${train.metrics.mq-consumers:}") String consumers) {
        this.rocketMQTemplate = rocketMQTemplate;
        for (String pair : consumers.split(",")) {
            int index = pair.indexOf(':');
            if (index <= 0 || index == pair.length() - 1) {
                if (!pair.trim().isEmpty()) {
                    LOG.warn("mq-consumers 配置格式应为 topic:group，已忽略：{}", pair);
                }
                continue;
            }
            String topic = pair.substring(0, index).trim();
            String group = pair.substring(index + 1).trim();
            if (topic.isEmpty() || group.isEmpty() || lagByTopic.containsKey(topic)) {
                continue;
            }
            AtomicLong lag = new AtomicLong();
            lagByTopic.put(topic, lag);
            groupByTopic.put(topic, group);
            Gauge.builder("mq_consume_lag", lag, AtomicLong::doubleValue)
                    .tag("topic", topic)
                    .register(meterRegistry);
        }
        LOG.info("MQ 消费积压采集已注册 topics={}", lagByTopic.keySet());
    }

    @Scheduled(fixedDelayString = "${train.metrics.mq-lag-refresh-ms:60000}",
            initialDelayString = "${train.metrics.mq-lag-initial-delay-ms:60000}")
    public void refresh() {
        if (lagByTopic.isEmpty()) {
            return;
        }
        MQClientInstance client = clientInstance();
        if (client == null) {
            return;
        }
        MQClientAPIImpl api = client.getMQClientAPIImpl();
        for (Map.Entry<String, AtomicLong> entry : lagByTopic.entrySet()) {
            String topic = entry.getKey();
            String group = groupByTopic.get(topic);
            try {
                entry.getValue().set(computeLag(api, topic, group));
            } catch (Exception e) {
                LOG.debug("MQ 消费积压采集失败 topic={} group={}：{}", topic, group, e.getMessage());
            }
        }
    }

    private MQClientInstance clientInstance() {
        try {
            DefaultMQProducer producer = rocketMQTemplate.getProducer();
            if (producer == null) {
                return null;
            }
            return producer.getDefaultMQProducerImpl().getMqClientFactory();
        } catch (Exception e) {
            LOG.debug("RocketMQ 客户端尚未就绪，跳过本轮积压采集：{}", e.getMessage());
            return null;
        }
    }

    private long computeLag(MQClientAPIImpl api, String topic, String group) throws Exception {
        TopicRouteData route = api.getTopicRouteInfoFromNameServer(topic, TIMEOUT_MS);
        if (route == null) {
            return 0L;
        }
        Map<String, String> masterAddrByBroker = new HashMap<>();
        for (BrokerData brokerData : route.getBrokerDatas()) {
            if (brokerData.getBrokerAddrs() == null) {
                continue;
            }
            String addr = brokerData.getBrokerAddrs().get(MixAll.MASTER_ID);
            if (addr != null) {
                masterAddrByBroker.put(brokerData.getBrokerName(), addr);
            }
        }

        long lag = 0L;
        for (QueueData queueData : route.getQueueDatas()) {
            if (!PermName.isWriteable(queueData.getPerm())) {
                continue;
            }
            String addr = masterAddrByBroker.get(queueData.getBrokerName());
            if (addr == null) {
                continue;
            }
            for (int queueId = 0; queueId < queueData.getWriteQueueNums(); queueId++) {
                lag += queueLag(api, addr, topic, group, queueId);
            }
        }
        return lag;
    }

    private long queueLag(MQClientAPIImpl api, String addr, String topic, String group, int queueId)
            throws Exception {
        QueryConsumerOffsetRequestHeader header = new QueryConsumerOffsetRequestHeader();
        header.setConsumerGroup(group);
        header.setTopic(topic);
        header.setQueueId(queueId);

        long consumerOffset;
        try {
            consumerOffset = api.queryConsumerOffset(addr, header, TIMEOUT_MS);
        } catch (MQBrokerException e) {
            if (e.getResponseCode() == ResponseCode.QUERY_NOT_FOUND) {
                consumerOffset = 0L;
            } else {
                throw e;
            }
        }
        long maxOffset = api.getMaxOffset(addr, topic, queueId, TIMEOUT_MS);
        return Math.max(0L, maxOffset - consumerOffset);
    }
}
