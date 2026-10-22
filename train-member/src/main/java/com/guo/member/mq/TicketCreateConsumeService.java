package com.guo.member.mq;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.alibaba.fastjson.JSON;
import com.guo.common.event.TicketCreateEvent;
import com.guo.common.req.TicketIssueReq;
import com.guo.common.req.TicketStatusUpdateReq;
import com.guo.common.resp.CommonResp;
import com.guo.member.feign.BusinessOrderFeign;
import com.guo.member.service.TicketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * I10 TICKET_CREATE 消费组 {@code member-ticket-create}：写会员车票并回写 business 出票状态。
 *
 * <p>幂等三层：Redis 去重键、member 唯一键 {@code uk_order_passenger}、business
 * ticket_status CAS。失败抛异常触发重投，超过 5 次回写 F 并进
 * {@code %DLQ%member-ticket-create}。</p>
 */
@Service
public class TicketCreateConsumeService {

    public static final String DLQ_TOPIC = "%DLQ%member-ticket-create";
    /** 与 RocketMQ maxReconsumeTimes 语义一致：首次投递后再重投 5 次 */
    public static final int MAX_RECONSUME_TIMES = 5;

    private static final Logger LOG = LoggerFactory.getLogger(TicketCreateConsumeService.class);
    private static final Duration DEDUP_TTL = Duration.ofHours(24);
    private static final Duration ATTEMPT_TTL = Duration.ofHours(24);
    private static final String DEDUP_PREFIX = "consume:ticket-create:done:";
    private static final String ATTEMPT_PREFIX = "consume:ticket-create:fail:";

    private static final String TICKET_STATUS_ISSUED = "S";
    private static final String TICKET_STATUS_ISSUING = "I";

    private final StringRedisTemplate stringRedisTemplate;
    private final TicketService ticketService;
    private final BusinessOrderFeign businessOrderFeign;
    private final TicketCreateDlqSender dlqSender;

    public TicketCreateConsumeService(StringRedisTemplate stringRedisTemplate,
                                      TicketService ticketService,
                                      BusinessOrderFeign businessOrderFeign,
                                      TicketCreateDlqSender dlqSender) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.ticketService = ticketService;
        this.businessOrderFeign = businessOrderFeign;
        this.dlqSender = dlqSender;
    }

    public void consume(String body) {
        Long orderId = orderIdOrNull(body);
        String key = orderId != null ? String.valueOf(orderId) : DigestUtil.sha256Hex(body);
        String dedupKey = DEDUP_PREFIX + key;

        Boolean first = stringRedisTemplate.opsForValue().setIfAbsent(dedupKey, "DONE", DEDUP_TTL);
        if (!Boolean.TRUE.equals(first)) {
            LOG.info("TICKET_CREATE 重复消息已忽略 orderId={}", key);
            return;
        }

        try {
            process(body, orderId);
            stringRedisTemplate.delete(ATTEMPT_PREFIX + key);
            LOG.info("TICKET_CREATE 消费成功 orderId={}", key);
        } catch (Exception e) {
            stringRedisTemplate.delete(dedupKey);
            int attempts = nextAttempt(key);
            if (attempts > MAX_RECONSUME_TIMES) {
                markFailedQuietly(orderId);
                dlqSender.send(body, key, attempts, e.getMessage());
                LOG.error("TICKET_CREATE 已重投 {} 次仍失败，出票置失败并转死信 orderId={}",
                        MAX_RECONSUME_TIMES, key, e);
                return;
            }
            LOG.warn("TICKET_CREATE 消费失败，第 {} 次，等待重投 orderId={}", attempts, key, e);
            throw new IllegalStateException(
                    "TICKET_CREATE 消费失败，第 " + attempts + " 次，触发重投", e);
        }
    }

    private void process(String body, Long orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("TICKET_CREATE 消息缺少 orderId");
        }
        TicketCreateEvent event = JSON.parseObject(body, TicketCreateEvent.class);
        if (event == null || CollUtil.isEmpty(event.getTickets())) {
            throw new IllegalArgumentException("TICKET_CREATE 消息缺少车票明细 orderId=" + orderId);
        }

        // 7.3 第 1 步：先确认 order 处于待出票状态，避免退款/重复事件导致重新发票
        CommonResp<String> statusResp = businessOrderFeign.ticketStatus(orderId);
        if (statusResp == null || !statusResp.getSuccess()) {
            throw new IllegalStateException("查询订单出票状态失败 orderId=" + orderId
                    + ", resp=" + statusResp);
        }
        String ticketStatus = statusResp.getContent();
        if (TICKET_STATUS_ISSUED.equals(ticketStatus)) {
            LOG.info("TICKET_CREATE 订单已出票，忽略 orderId={}", orderId);
            return;
        }
        if (!TICKET_STATUS_ISSUING.equals(ticketStatus)) {
            LOG.warn("TICKET_CREATE 订单不处于待出票状态，忽略 orderId={} ticketStatus={}",
                    orderId, ticketStatus);
            return;
        }

        TicketIssueReq req = new TicketIssueReq();
        req.setOrderId(orderId);
        req.setTickets(event.getTickets());
        ticketService.issue(req);

        CommonResp<Void> updateResp = businessOrderFeign.updateTicketStatus(
                new TicketStatusUpdateReq(orderId, true, null));
        if (updateResp == null || !updateResp.getSuccess()) {
            throw new IllegalStateException("回写出票成功状态失败 orderId=" + orderId
                    + ", resp=" + updateResp);
        }
    }

    private void markFailedQuietly(Long orderId) {
        if (orderId == null) {
            return;
        }
        try {
            businessOrderFeign.updateTicketStatus(new TicketStatusUpdateReq(orderId, false, "重投超限"));
        } catch (Exception e) {
            LOG.warn("TICKET_CREATE 回写出票失败状态异常 orderId={}", orderId, e);
        }
    }

    private Long orderIdOrNull(String body) {
        try {
            TicketCreateEvent event = JSON.parseObject(body, TicketCreateEvent.class);
            return event == null ? null : event.getOrderId();
        } catch (Exception ignored) {
            return null;
        }
    }

    private int nextAttempt(String key) {
        String attemptKey = ATTEMPT_PREFIX + key;
        Long attempts = stringRedisTemplate.opsForValue().increment(attemptKey);
        int value = attempts == null ? 1 : attempts.intValue();
        if (value == 1) {
            stringRedisTemplate.expire(attemptKey, ATTEMPT_TTL);
        }
        return value;
    }
}
