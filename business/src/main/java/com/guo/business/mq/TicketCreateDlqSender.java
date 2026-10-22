package com.guo.business.mq;

import cn.hutool.core.util.StrUtil;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * I10 TICKET_CREATE 死信投递：超过重投上限的消息写入
 * {@code %DLQ%business-ticket-create}，供人工重放。
 */
@Component
public class TicketCreateDlqSender {

    private static final Logger LOG = LoggerFactory.getLogger(TicketCreateDlqSender.class);

    private final RocketMQTemplate rocketMQTemplate;

    public TicketCreateDlqSender(RocketMQTemplate rocketMQTemplate) {
        this.rocketMQTemplate = rocketMQTemplate;
    }

    public void send(String body, String key, int attempts, String reason) {
        Message message = new Message(TicketCreateConsumeService.DLQ_TOPIC,
                body.getBytes(StandardCharsets.UTF_8));
        message.setKeys(key);
        message.putUserProperty("attempts", String.valueOf(attempts));
        message.putUserProperty("reason", StrUtil.maxLength(StrUtil.blankToDefault(reason, "unknown"), 200));
        try {
            rocketMQTemplate.getProducer().send(message);
            LOG.info("TICKET_CREATE 已写入死信 key={} attempts={}", key, attempts);
        } catch (Exception e) {
            throw new IllegalStateException("写入死信失败, key=" + key, e);
        }
    }
}
