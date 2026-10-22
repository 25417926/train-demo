package com.guo.business.mq;

import cn.hutool.crypto.digest.DigestUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.guo.business.domain.Confirm_Order;
import com.guo.business.mapper.OrderConfirmPersistMapper;
import com.guo.business.service.TicketIssueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * I10 TICKET_CREATE 消费：调用 member 幂等出票，成功后回写 ticket_status=S 与 outbox。
 *
 * <p>幂等三层：Redis 去重键、member 唯一键 {@code uk_order_passenger}、
 * confirm_order.ticket_status CAS。失败抛异常触发 RocketMQ 重投，超过 5 次转死信。</p>
 */
@Service
public class TicketCreateConsumeService {

    public static final String DLQ_TOPIC = "%DLQ%business-ticket-create";
    /** 与 ORDER_CONFIRM 一致：首次投递后再重投 5 次 */
    public static final int MAX_RECONSUME_TIMES = 5;

    private static final Logger LOG = LoggerFactory.getLogger(TicketCreateConsumeService.class);
    private static final Duration DEDUP_TTL = Duration.ofHours(24);
    private static final Duration ATTEMPT_TTL = Duration.ofHours(24);
    private static final String DEDUP_PREFIX = "consume:ticket-create:done:";
    private static final String ATTEMPT_PREFIX = "consume:ticket-create:fail:";
    private static final String EVENT_ID_PREFIX = "TICKET_CREATE:";

    private static final String TICKET_STATUS_ISSUED = "S";
    private static final String TICKET_STATUS_ISSUING = "I";

    private final StringRedisTemplate stringRedisTemplate;
    private final OrderConfirmPersistMapper persistMapper;
    private final TicketIssueService ticketIssueService;
    private final TicketCreateDlqSender dlqSender;

    public TicketCreateConsumeService(StringRedisTemplate stringRedisTemplate,
                                      OrderConfirmPersistMapper persistMapper,
                                      TicketIssueService ticketIssueService,
                                      TicketCreateDlqSender dlqSender) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.persistMapper = persistMapper;
        this.ticketIssueService = ticketIssueService;
        this.dlqSender = dlqSender;
    }

    public void consume(String body) {
        String key = keyOf(body);
        String dedupKey = DEDUP_PREFIX + key;

        Boolean first = stringRedisTemplate.opsForValue().setIfAbsent(dedupKey, "DONE", DEDUP_TTL);
        if (!Boolean.TRUE.equals(first)) {
            LOG.info("TICKET_CREATE 重复消息已忽略 key={}", key);
            return;
        }

        try {
            process(orderIdOf(body));
            stringRedisTemplate.delete(ATTEMPT_PREFIX + key);
            LOG.info("TICKET_CREATE 消费成功 key={}", key);
        } catch (Exception e) {
            stringRedisTemplate.delete(dedupKey);
            int attempts = nextAttempt(key);
            if (attempts > MAX_RECONSUME_TIMES) {
                markTicketFailedQuietly(body);
                dlqSender.send(body, key, attempts, e.getMessage());
                LOG.error("TICKET_CREATE 已重投 {} 次仍失败，出票置失败并转死信 key={}",
                        MAX_RECONSUME_TIMES, key, e);
                return;
            }
            LOG.warn("TICKET_CREATE 消费失败，第 {} 次，等待重投 key={}", attempts, key, e);
            throw new IllegalStateException(
                    "TICKET_CREATE 消费失败，第 " + attempts + " 次，触发重投", e);
        }
    }

    private void process(Long orderId) {
        Confirm_Order order = persistMapper.selectOrderById(orderId);
        if (order == null) {
            LOG.warn("TICKET_CREATE 订单不存在，忽略 orderId={}", orderId);
            return;
        }

        String ticketStatus = order.getTicketStatus();
        if (TICKET_STATUS_ISSUED.equals(ticketStatus)) {
            LOG.info("TICKET_CREATE 订单已出票，忽略 orderId={}", orderId);
            markOutboxSent(orderId);
            return;
        }
        if (!TICKET_STATUS_ISSUING.equals(ticketStatus) || !"S".equals(order.getStatus())) {
            LOG.warn("TICKET_CREATE 订单不处于待出票状态，忽略 orderId={} status={} ticketStatus={}",
                    orderId, order.getStatus(), ticketStatus);
            return;
        }

        ticketIssueService.issueTickets(order);

        // 并发重复消息可能已把 ticket_status 置 S，此时视为成功
        if (persistMapper.markTicketSuccess(orderId) != 1) {
            Confirm_Order latest = persistMapper.selectOrderById(orderId);
            if (latest == null || !TICKET_STATUS_ISSUED.equals(latest.getTicketStatus())) {
                throw new IllegalStateException("订单出票状态回写失败 orderId=" + orderId);
            }
        }
        markOutboxSent(orderId);
    }

    private void markTicketFailedQuietly(String body) {
        try {
            Long orderId = orderIdOf(body);
            persistMapper.markTicketFailed(orderId);
        } catch (Exception e) {
            LOG.warn("TICKET_CREATE 出票失败状态回写失败 body={}", body, e);
        }
    }

    private void markOutboxSent(Long orderId) {
        try {
            persistMapper.markOutboxSent(EVENT_ID_PREFIX + orderId);
        } catch (Exception e) {
            // 审计记录状态不影响出票结果
            LOG.warn("TICKET_CREATE outbox 状态更新失败 orderId={}", orderId, e);
        }
    }

    private Long orderIdOf(String body) {
        Long orderId = orderIdOrNull(body);
        if (orderId == null) {
            throw new IllegalArgumentException("TICKET_CREATE 消息缺少 orderId");
        }
        return orderId;
    }

    private Long orderIdOrNull(String body) {
        try {
            JSONObject json = JSON.parseObject(body);
            return json == null ? null : json.getLong("orderId");
        } catch (Exception ignored) {
            return null;
        }
    }

    private String keyOf(String body) {
        Long orderId = orderIdOrNull(body);
        return orderId != null ? String.valueOf(orderId) : DigestUtil.sha256Hex(body);
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
