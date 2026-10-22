package com.guo.business.service;

import com.guo.business.TypeEnum.OrderCloseReasonEnum;
import com.guo.business.inventory.InventoryKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * I12 超时关闭延迟队列：order:close ZSet，score=expireAt 毫秒，member=orderId。
 *
 * <p>队列只是触发索引，丢失时由 {@link OrderCloseScheduler} 扫表兜底；
 * 因此这里的入队/出队失败只记录日志，不影响主流程。</p>
 */
@Component
public class OrderCloseQueue {

    private static final Logger LOG = LoggerFactory.getLogger(OrderCloseQueue.class);

    private final StringRedisTemplate redis;

    public OrderCloseQueue(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 订单进入待关闭窗口时入队；同一订单重复入队会被 score 覆盖。
     */
    public void schedule(Long orderId, Date expireAt) {
        if (orderId == null || expireAt == null) {
            return;
        }
        try {
            redis.opsForZSet().add(InventoryKey.ORDER_CLOSE, String.valueOf(orderId), expireAt.getTime());
        } catch (Exception e) {
            LOG.warn("订单关闭队列入队失败，等待扫表兜底 orderId={} expireAt={}", orderId, expireAt, e);
        }
    }

    /**
     * 支付成功或订单已关闭时出队。
     */
    public void cancel(Long orderId) {
        if (orderId == null) {
            return;
        }
        try {
            redis.opsForZSet().remove(InventoryKey.ORDER_CLOSE, String.valueOf(orderId));
        } catch (Exception e) {
            LOG.warn("订单关闭队列出队失败 orderId={}", orderId, e);
        }
    }

    /**
     * 取出 score <= now 的订单号，按到期时间升序。
     */
    public List<Long> due(long now, int limit) {        List<Long> orderIds = new ArrayList<>();
        Set<String> members = redis.opsForZSet()
                .rangeByScore(InventoryKey.ORDER_CLOSE, 0, now, 0, Math.max(limit, 1));
        if (members == null || members.isEmpty()) {
            return orderIds;
        }
        for (String member : members) {
            try {
                orderIds.add(Long.valueOf(member));
            } catch (NumberFormatException e) {
                LOG.warn("订单关闭队列存在非法成员，已移除 member={}", member);
                redis.opsForZSet().remove(InventoryKey.ORDER_CLOSE, member);
            }
        }
        return orderIds;
    }
}
