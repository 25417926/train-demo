package com.guo.business.mq;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * I10 出票事件投递：出票落库事务提交后发送 TICKET_CREATE，由消费端异步写会员车票。
 *
 * <p>消息体只带 orderId/requestId，消费端按订单重建车票明细；重复投递由
 * member 侧 {@code uk_order_passenger} 与消费端去重键共同保证幂等。</p>
 */
@Component
public class TicketCreateProducer {

    public static final String TOPIC = "ticket-create";

    private static final Logger LOG = LoggerFactory.getLogger(TicketCreateProducer.class);

    private final RocketMQTemplate rocketMQTemplate;

    public TicketCreateProducer(RocketMQTemplate rocketMQTemplate) {
        this.rocketMQTemplate = rocketMQTemplate;
    }

    public void send(Long orderId, String requestId) {
        JSONObject payload = new JSONObject(true);
        payload.put("orderId", orderId);
        payload.put("requestId", requestId);

        SendResult result = rocketMQTemplate.syncSend(TOPIC, JSON.toJSONString(payload));
        if (result == null || result.getSendStatus() != SendStatus.SEND_OK) {
            throw new IllegalStateException("TICKET_CREATE 投递失败 orderId=" + orderId
                    + ", status=" + (result == null ? "null" : result.getSendStatus()));
        }
        LOG.info("TICKET_CREATE 已投递 orderId={} msgId={}", orderId, result.getMsgId());
    }
}
