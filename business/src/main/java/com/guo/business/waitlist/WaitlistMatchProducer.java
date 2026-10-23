package com.guo.business.waitlist;

import com.alibaba.fastjson.JSON;
import com.guo.business.observability.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

/**
 * INVENTORY_RELEASED 事件生产者（I16）。
 *
 * <p>binding {@code inventoryReleasedProducer-out-0} 对应 topic {@code inventory-released}，
 * 消费组 {@code business-waitlist-match}。事件在释放事务提交后发送，投递失败由 outbox
 * 审计记录保留，等待人工或后续补偿。</p>
 */
@Service
public class WaitlistMatchProducer {

    private static final Logger LOG = LoggerFactory.getLogger(WaitlistMatchProducer.class);

    static final String OUTPUT_BINDING = "inventoryReleasedProducer-out-0";

    private final StreamBridge streamBridge;

    public WaitlistMatchProducer(StreamBridge streamBridge) {
        this.streamBridge = streamBridge;
    }

    public boolean publish(WaitlistMatchEvent event) {
        if (event == null || event.getEventId() == null || event.getSeatType() == null) {
            return false;
        }
        Message<String> message = MessageBuilder
                .withPayload(JSON.toJSONString(event))
                .setHeader("KEYS", event.getEventId())
                .setHeader(TraceContext.MQ_TRACE_HEADER, TraceContext.traceIdOrNew())
                .setHeader(TraceContext.MQ_REQUEST_HEADER, event.getEventId())
                .build();
        try {
            boolean sent = streamBridge.send(OUTPUT_BINDING, message);
            LOG.info("候补匹配事件已投递 eventId={} bucket={} sent={}",
                    event.getEventId(), event.bucket(), sent);
            return sent;
        } catch (Exception e) {
            LOG.error("候补匹配事件投递失败，等待 outbox 补偿 eventId={}", event.getEventId(), e);
            return false;
        }
    }
}
