package com.guo.business.service;

import com.guo.business.domain.Confirm_Order;
import com.guo.business.mapper.OrderConfirmPersistMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * I10 出票结果回写：member 消费端出票完成后经 Feign 通知 business。
 *
 * <p>business 与 member 各持独立库，出票状态只能由 business 写本库。</p>
 */
@Service
public class OrderTicketStatusService {

    private static final Logger LOG = LoggerFactory.getLogger(OrderTicketStatusService.class);
    private static final String TICKET_STATUS_ISSUED = "S";
    private static final String EVENT_ID_PREFIX = "TICKET_CREATE:";

    private final OrderConfirmPersistMapper persistMapper;

    public OrderTicketStatusService(OrderConfirmPersistMapper persistMapper) {
        this.persistMapper = persistMapper;
    }

    /**
     * 返回订单当前 ticket_status；订单不存在返回 null
     */
    public String selectTicketStatus(Long orderId) {
        Confirm_Order order = persistMapper.selectOrderById(orderId);
        return order == null ? null : order.getTicketStatus();
    }

    public void markIssued(Long orderId) {
        // 并发重复通知时 CAS 可能返回 0，只要最终状态是 S 即视为成功
        if (persistMapper.markTicketSuccess(orderId) != 1) {
            Confirm_Order latest = persistMapper.selectOrderById(orderId);
            if (latest == null || !TICKET_STATUS_ISSUED.equals(latest.getTicketStatus())) {
                throw new IllegalStateException("出票状态回写失败 orderId=" + orderId);
            }
        }
        persistMapper.markOutboxSent(EVENT_ID_PREFIX + orderId);
        LOG.info("订单[{}]出票状态已回写为 S", orderId);
    }

    public void markFailed(Long orderId) {
        int rows = persistMapper.markTicketFailed(orderId);
        LOG.warn("订单[{}]出票置失败 ticket_status=F，影响行数={}", orderId, rows);
    }
}
