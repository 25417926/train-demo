package com.guo.business.mq;

import com.guo.business.resp.DlqMessageResp;
import org.apache.rocketmq.client.consumer.DefaultMQPullConsumer;
import org.apache.rocketmq.client.consumer.PullResult;
import org.apache.rocketmq.client.consumer.PullStatus;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * I08 死信查询与人工重放。
 *
 * <p>不引入 rocketmq-tools：用已有的 Pull 消费者直接扫描
 * {@code %DLQ%business-order-confirm}，只读接口按上限返回消息。</p>
 */
@Service
public class OrderConfirmDlqService {

    private static final Logger LOG = LoggerFactory.getLogger(OrderConfirmDlqService.class);
    private static final int MAX_SCAN = 200;

    @Value("${rocketmq.name-server}")
    private String nameServer;

    private final RocketMQTemplate rocketMQTemplate;

    public OrderConfirmDlqService(RocketMQTemplate rocketMQTemplate) {
        this.rocketMQTemplate = rocketMQTemplate;
    }

    public List<DlqMessageResp> query(int maxNum) {
        int limit = Math.max(1, Math.min(maxNum, MAX_SCAN));
        DefaultMQPullConsumer consumer = new DefaultMQPullConsumer("business-dlq-query");
        consumer.setNamesrvAddr(nameServer);
        List<DlqMessageResp> result = new ArrayList<>();
        try {
            consumer.start();
            Set<MessageQueue> queues = consumer.fetchSubscribeMessageQueues(OrderConfirmConsumeService.DLQ_TOPIC);
            for (MessageQueue queue : queues) {
                long offset = consumer.minOffset(queue);
                while (result.size() < limit) {
                    PullResult pullResult = consumer.pull(queue, "*", offset,
                            Math.min(32, limit - result.size()));
                    if (pullResult.getMsgFoundList() != null) {
                        for (MessageExt message : pullResult.getMsgFoundList()) {
                            result.add(toResp(message));
                        }
                    }
                    if (pullResult.getPullStatus() != PullStatus.FOUND) {
                        break;
                    }
                    offset = pullResult.getNextBeginOffset();
                }
                if (result.size() >= limit) {
                    break;
                }
            }
        } catch (MQClientException e) {
            LOG.warn("查询死信失败，topic={}，可能是暂无死信或 NameServer 不可用",
                    OrderConfirmConsumeService.DLQ_TOPIC, e);
            return Collections.emptyList();
        } catch (Exception e) {
            LOG.error("查询死信异常，topic={}", OrderConfirmConsumeService.DLQ_TOPIC, e);
            return Collections.emptyList();
        } finally {
            consumer.shutdown();
        }
        return result;
    }

    /**
     * 按 msgId 找到死信消息并重新投递到 order-confirm，由消费组再次处理。
     */
    public void replay(String msgId) {
        DlqMessageResp target = query(MAX_SCAN).stream()
                .filter(item -> msgId.equals(item.getMsgId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("死信消息不存在或已超过扫描上限, msgId=" + msgId));
        Message message = new Message(OrderConfirmConsumeService.ORDER_CONFIRM_TOPIC,
                target.getBody().getBytes(StandardCharsets.UTF_8));
        message.setKeys(target.getKeys());
        try {
            rocketMQTemplate.getProducer().send(message);
        } catch (Exception e) {
            throw new IllegalStateException("死信重放失败, msgId=" + msgId, e);
        }
        LOG.info("死信重放成功 msgId={} keys={}", msgId, target.getKeys());
    }

    private DlqMessageResp toResp(MessageExt message) {
        DlqMessageResp resp = new DlqMessageResp();
        resp.setMsgId(message.getMsgId());
        resp.setTopic(message.getTopic());
        resp.setKeys(message.getKeys());
        resp.setReconsumeTimes(message.getReconsumeTimes());
        resp.setStoreTimestamp(message.getStoreTimestamp());
        resp.setBody(new String(message.getBody(), StandardCharsets.UTF_8));
        return resp;
    }
}
