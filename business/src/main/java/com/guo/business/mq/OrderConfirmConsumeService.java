package com.guo.business.mq;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * ORDER_CONFIRM 消费入口（I08）：幂等去重 + 失败计数 + 达到上限转死信。
 *
 * <p>本阶段只做链路可靠性，落库逻辑在 I09 实现，因此成功分支仅解析并记录日志。</p>
 *
 * <p>重投说明：binder 2021.0.5.0 的 {@code push.maxReconsumeTimes} 不会下发到
 * RocketMQ 客户端，这里用 Redis 计数自行控制“最多 5 次投递，超过进
 * {@code %DLQ%business-order-confirm}”，抛异常即触发 Broker 重投。</p>
 */
@Service
public class OrderConfirmConsumeService {

    public static final String DLQ_TOPIC = "%DLQ%business-order-confirm";
    public static final String ORDER_CONFIRM_TOPIC = "order-confirm";
    /** 与 RocketMQ maxReconsumeTimes 语义一致：首次投递后再重投 5 次 */
    public static final int MAX_RECONSUME_TIMES = 5;

    private static final Logger LOG = LoggerFactory.getLogger(OrderConfirmConsumeService.class);
    private static final Duration DEDUP_TTL = Duration.ofHours(24);
    private static final Duration ATTEMPT_TTL = Duration.ofHours(24);
    private static final String DEDUP_PREFIX = "consume:order-confirm:done:";
    private static final String ATTEMPT_PREFIX = "consume:order-confirm:fail:";

    private final StringRedisTemplate stringRedisTemplate;
    private final OrderConfirmDlqSender dlqSender;
    private final OrderConfirmTicketService ticketService;

    public OrderConfirmConsumeService(StringRedisTemplate stringRedisTemplate,
                                      OrderConfirmDlqSender dlqSender,
                                      OrderConfirmTicketService ticketService) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.dlqSender = dlqSender;
        this.ticketService = ticketService;
    }

    public void consume(String body) {
        String requestId = requestIdOf(body);
        String dedupKey = DEDUP_PREFIX + requestId;

        Boolean first = stringRedisTemplate.opsForValue().setIfAbsent(dedupKey, "DONE", DEDUP_TTL);
        if (!Boolean.TRUE.equals(first)) {
            LOG.info("ORDER_CONFIRM 重复消息已忽略 requestId={}", requestId);
            return;
        }

        try {
            process(body, requestId);
            stringRedisTemplate.delete(ATTEMPT_PREFIX + requestId);
            LOG.info("ORDER_CONFIRM 消费成功 requestId={}", requestId);
        } catch (Exception e) {
            // 失败时释放去重键，保证下一次重投仍会执行
            stringRedisTemplate.delete(dedupKey);
            int attempts = nextAttempt(requestId);
            if (attempts > MAX_RECONSUME_TIMES) {
                dlqSender.send(body, requestId, attempts, e.getMessage());
                LOG.error("ORDER_CONFIRM 已重投 {} 次仍失败，转入死信 requestId={}", MAX_RECONSUME_TIMES, requestId, e);
                return;
            }
            LOG.warn("ORDER_CONFIRM 消费失败，第 {} 次，等待重投 requestId={}", attempts, requestId, e);
            throw new IllegalStateException(
                    "ORDER_CONFIRM 消费失败，第 " + attempts + " 次，触发重投", e);
        }
    }

    /**
     * 解析消息并交给 I09 落库；解析失败与落库异常都会触发重投。
     */
    private void process(String body, String requestId) {
        JSONObject json = JSON.parseObject(body);
        Long orderId = json == null ? null : json.getLong("orderId");
        String holdToken = json == null ? null : json.getString("holdToken");
        if (orderId == null || StrUtil.isBlank(holdToken)) {
            throw new IllegalArgumentException("ORDER_CONFIRM 消息缺少 orderId/holdToken, requestId=" + requestId);
        }
        ticketService.handle(orderId, requestId, holdToken);
    }

    private int nextAttempt(String requestId) {
        String key = ATTEMPT_PREFIX + requestId;
        Long attempts = stringRedisTemplate.opsForValue().increment(key);
        int value = attempts == null ? 1 : attempts.intValue();
        if (value == 1) {
            stringRedisTemplate.expire(key, ATTEMPT_TTL);
        }
        return value;
    }

    private String requestIdOf(String body) {
        try {
            String requestId = JSON.parseObject(body).getString("requestId");
            if (StrUtil.isNotBlank(requestId)) {
                return requestId;
            }
        } catch (Exception ignored) {
            // 消息体不可解析时用摘要作为兜底键，保证重投计数仍然有效
        }
        return DigestUtil.sha256Hex(body);
    }
}
