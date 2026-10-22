package com.guo.business.service;

import com.guo.business.TypeEnum.OrderCloseReasonEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * I12 超时关闭调度：Redis ZSet 主触发 + 每 5 分钟扫表兜底。
 *
 * <p>ZSet 扫描线程与兜底线程都可能命中同一订单，关单事务的状态 CAS 保证只有一次生效；
 * 扫码线程先出队再关单，失败后由扫表兜底，不会反复领取同一订单。</p>
 */
@Component
@ConditionalOnProperty(prefix = "train.close", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OrderCloseScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(OrderCloseScheduler.class);

    private final OrderCloseQueue orderCloseQueue;
    private final OrderCloseService orderCloseService;
    private final int pollBatch;
    private final int sweepBatch;

    public OrderCloseScheduler(OrderCloseQueue orderCloseQueue,
                               OrderCloseService orderCloseService,
                               @Value("${train.close.poll-batch:100}") int pollBatch,
                               @Value("${train.close.sweep-batch:200}") int sweepBatch) {
        this.orderCloseQueue = orderCloseQueue;
        this.orderCloseService = orderCloseService;
        this.pollBatch = Math.max(pollBatch, 1);
        this.sweepBatch = Math.max(sweepBatch, 1);
    }

    /**
     * 主触发：领取 score <= now 的订单并关闭。
     */
    @Scheduled(fixedDelayString = "${train.close.poll-interval-ms:5000}",
            initialDelayString = "${train.close.poll-initial-delay-ms:10000}")
    public void pollDueOrders() {
        List<Long> orderIds;
        try {
            orderIds = orderCloseQueue.due(System.currentTimeMillis(), pollBatch);
        } catch (Exception e) {
            LOG.error("读取订单关闭队列失败，等待扫表兜底", e);
            return;
        }
        for (Long orderId : orderIds) {
            // 先出队再关单：关单失败由扫表兜底，避免同一订单被反复领取
            orderCloseQueue.cancel(orderId);
            closeQuietly(orderId, OrderCloseReasonEnum.TIMEOUT);
        }
    }

    /**
     * 兜底：ZSet 丢数据时按 expire_at 扫表关闭。
     */
    @Scheduled(fixedDelayString = "${train.close.sweep-interval-ms:300000}",
            initialDelayString = "${train.close.sweep-initial-delay-ms:60000}")
    public void sweepExpiredOrders() {
        List<Long> orderIds;
        try {
            orderIds = orderCloseService.selectExpiredOrderIds(sweepBatch);
        } catch (Exception e) {
            LOG.error("扫描超时订单失败", e);
            return;
        }
        for (Long orderId : orderIds) {
            closeQuietly(orderId, OrderCloseReasonEnum.TIMEOUT);
        }
    }

    private void closeQuietly(Long orderId, OrderCloseReasonEnum reason) {
        try {
            orderCloseService.close(orderId, reason);
        } catch (Exception e) {
            LOG.error("超时关单失败，等待下一次调度 orderId={}", orderId, e);
        }
    }
}
