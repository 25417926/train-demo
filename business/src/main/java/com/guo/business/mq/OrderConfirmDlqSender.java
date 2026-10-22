package com.guo.business.mq;

import cn.hutool.core.util.StrUtil;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * I08 死信投递：把超过重投上限的 ORDER_CONFIRM 消息写入
 * {@code %DLQ%business-order-confirm}，供查询接口与人工重放使用。
 */
@Component
public class OrderConfirmDlqSender {

    private static final Logger LOG = LoggerFactory.getLogger(OrderConfirmDlqSender.class);

    private final RocketMQTemplate rocketMQTemplate;

    public OrderConfirmDlqSender(RocketMQTemplate rocketMQTemplate) {
        this.rocketMQTemplate = rocketMQTemplate;
    }

    public void send(String body, String requestId, int attempts, String reason) {
        Message message = new Message(OrderConfirmConsumeService.DLQ_TOPIC,
                body.getBytes(StandardCharsets.UTF_8));
        message.setKeys(requestId);
        message.putUserProperty("requestId", requestId);
        message.putUserProperty("attempts", String.valueOf(attempts));
        message.putUserProperty("reason", StrUtil.maxLength(StrUtil.blankToDefault(reason, "unknown"), 200));
        try {
            rocketMQTemplate.getProducer().send(message);
            LOG.info("ORDER_CONFIRM 已写入死信 requestId={} attempts={}", requestId, attempts);
        } catch (Exception e) {
            throw new IllegalStateException("写入死信失败, requestId=" + requestId, e);
        }
    }
}
