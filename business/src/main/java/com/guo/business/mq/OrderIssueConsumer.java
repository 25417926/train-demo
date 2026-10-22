package com.guo.business.mq;

import com.alibaba.fastjson.JSON;
import com.guo.business.req.OrderIssueDto;
import com.guo.business.service.TicketIssueService;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 支付成功后的出票消费者。
 * 出票失败只记录 ticket_status=F，不抛异常（重试与座位释放由 I04 处理）。
 */
@Service
@RocketMQMessageListener(consumerGroup = "order_issue_consumer_group", topic = "order_issue")
public class OrderIssueConsumer implements RocketMQListener<MessageExt> {

    private static final Logger LOG = LoggerFactory.getLogger(OrderIssueConsumer.class);

    @Resource
    private TicketIssueService ticketIssueService;

    @Override
    public void onMessage(MessageExt messageExt) {
        String body = new String(messageExt.getBody());
        LOG.info("OrderIssueConsumer 收到消息：{}", body);
        try {
            OrderIssueDto dto = JSON.parseObject(body, OrderIssueDto.class);
            MDC.put("LOG_ID", dto.getLogId());
            ticketIssueService.issue(dto.getOrderId());
        } catch (Exception e) {
            LOG.error("出票消息处理异常：{}", body, e);
        } finally {
            MDC.clear();
        }
    }
}
