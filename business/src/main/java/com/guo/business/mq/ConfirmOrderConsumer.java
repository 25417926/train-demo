package com.guo.business.mq;

import com.alibaba.fastjson.JSON;
import com.guo.business.req.Confirm_OrderDto;
import com.guo.business.service.Confirm_OrderService;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * @Description
 * @Author: 派
 * @Date: 2025/1/14
 */
@Service
/**
 * 监听RocketMQ消息队列中默认主题的消费者类
 * 该类实现了RocketMQListener接口，用于处理消息队列中的消息
 * 主要功能是：接收并处理订单确认消息
 */
@RocketMQMessageListener(consumerGroup = "default",topic = "order_confirm")
public class ConfirmOrderConsumer implements RocketMQListener<MessageExt> {
    // 日志对象，用于记录日志信息
    private Logger LOG = LoggerFactory.getLogger(ConfirmOrderConsumer.class);
    @Resource
    private Confirm_OrderService confirm_OrderService;
    /**
     * 当有消息到达时调用的方法
     * 该方法接收一个参数：messageExt，这是接收到的消息对象
     * 它将消息体以字符串形式打印出来
     *
     * @param messageExt 接收到的消息对象，包含消息的各种属性和内容
     */
    @Override
    public void onMessage(MessageExt messageExt) {
        // 获取消息体
        byte [] body = messageExt.getBody();

        // 将消息体以字符串形式打印出来
        LOG.info("ConfirmOrderConsumer{}",new String(body));
        Confirm_OrderDto req = JSON.parseObject(new String(body),Confirm_OrderDto.class);
        MDC.put("LOG_ID",req.getLogId());
        confirm_OrderService.DoStorage(req);
    }
}
