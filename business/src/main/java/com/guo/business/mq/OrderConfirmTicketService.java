package com.guo.business.mq;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.guo.business.TypeEnum.TrainCarriageTypeEnum;
import com.guo.business.domain.Confirm_Order;
import com.guo.business.exception.SeatConflictException;
import com.guo.business.inventory.ConfirmCommand;
import com.guo.business.inventory.InventoryKey;
import com.guo.business.inventory.InventoryService;
import com.guo.business.inventory.ReleaseCommand;
import com.guo.business.mapper.OrderConfirmPersistMapper;
import com.guo.business.purchase.model.InventoryHoldRecord;
import com.guo.business.purchase.model.SeatRecord;
import com.guo.business.req.Confirm_OrderTicketReq;
import com.guo.common.util.SnowUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * I09 出票落库：订单 CAS、inventory_hold、座位 version CAS、余票 delta、资格消费。
 *
 * <p>幂等由订单状态 CAS 与 {@code uk_hold_seat} 双保险；座位冲突整单回滚后重试 3 次，
 * 仍失败则释放预扣并把订单置失败（明确失败，不再重投）。</p>
 */
@Service
public class OrderConfirmTicketService {

    private static final Logger LOG = LoggerFactory.getLogger(OrderConfirmTicketService.class);
    private static final int SEAT_RETRY_TIMES = 3;
    /** 出票成功后支付窗口：5 分钟（docs 7.2） */
    private static final long PAYMENT_WINDOW_MS = 5 * 60 * 1000L;

    private final OrderConfirmPersistMapper persistMapper;
    private final InventoryService inventoryService;
    private final StringRedisTemplate redis;
    private final TransactionTemplate transactionTemplate;

    public OrderConfirmTicketService(OrderConfirmPersistMapper persistMapper,
                                     InventoryService inventoryService,
                                     StringRedisTemplate redis,
                                     PlatformTransactionManager transactionManager) {
        this.persistMapper = persistMapper;
        this.inventoryService = inventoryService;
        this.redis = redis;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void handle(Long orderId, String requestId, String holdToken) {
        Confirm_Order order = persistMapper.selectOrderByRequestId(requestId);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在, requestId=" + requestId);
        }
        if (!orderId.equals(order.getId())) {
            throw new IllegalArgumentException("消息 orderId 与订单不一致, requestId=" + requestId);
        }
        if ("S".equals(order.getStatus())) {
            LOG.info("ORDER_CONFIRM 重复消息，订单已出票 orderId={}", orderId);
            return;
        }
        if ("I".equals(order.getStatus())) {
            if (persistMapper.casStatus(orderId, "I", "P") == 0) {
                Confirm_Order latest = persistMapper.selectOrderByRequestId(requestId);
                if (latest == null || !"P".equals(latest.getStatus())) {
                    LOG.info("ORDER_CONFIRM 订单状态已变更，跳过 orderId={} status={}",
                            orderId, latest == null ? "null" : latest.getStatus());
                    return;
                }
                order = latest;
            }
        } else if (!"P".equals(order.getStatus())) {
            LOG.info("ORDER_CONFIRM 订单不可出票，跳过 orderId={} status={}", orderId, order.getStatus());
            return;
        }

        // 上一次投递已提交过事务但未 ACK：直接跳过
        if (persistMapper.countHoldsByOrderId(orderId) > 0) {
            LOG.info("ORDER_CONFIRM inventory_hold 已存在，跳过 orderId={}", orderId);
            return;
        }

        Map<Object, Object> hold = redis.opsForHash().entries(InventoryKey.hold(holdToken));
        if (hold.isEmpty() || !String.valueOf(orderId).equals(str(hold.get("orderId")))) {
            LOG.warn("ORDER_CONFIRM hold 不存在或不属于本订单，订单置失败 orderId={} holdToken={}",
                    orderId, holdToken);
            persistMapper.markFailed(orderId, "HOLD_MISSING");
            return;
        }

        List<Confirm_OrderTicketReq> tickets = JSON.parseArray(order.getTickets(), Confirm_OrderTicketReq.class);
        List<String> seatIds = splitCsv(str(hold.get("seatIds")));
        if (tickets == null || tickets.isEmpty() || tickets.size() != seatIds.size()) {
            throw new IllegalArgumentException("订单票数与座位数不一致, orderId=" + orderId
                    + ", tickets=" + (tickets == null ? 0 : tickets.size())
                    + ", seats=" + seatIds.size());
        }
        int startIndex = Integer.parseInt(str(hold.get("startIndex")));
        int endIndex = Integer.parseInt(str(hold.get("endIndex")));
        String dbSeatType = tickets.get(0).getSeatTypeCode();

        try {
            persistWithRetry(order, holdToken, seatIds, tickets, startIndex, endIndex);
        } catch (SeatConflictException e) {
            LOG.error("ORDER_CONFIRM 座位冲突重试 {} 次仍失败，释放并置失败 orderId={}",
                    SEAT_RETRY_TIMES, orderId, e);
            releaseQuietly(order, requestId, seatIds, tickets, dbSeatType, startIndex, endIndex);
            persistMapper.markFailed(orderId, "SEAT_CONFLICT");
            return;
        }

        confirmRedisQuietly(order, holdToken, tickets);
    }

    private void persistWithRetry(Confirm_Order order,
                                  String holdToken,
                                  List<String> seatIds,
                                  List<Confirm_OrderTicketReq> tickets,
                                  int startIndex,
                                  int endIndex) {
        for (int attempt = 1; attempt <= SEAT_RETRY_TIMES; attempt++) {
            try {
                transactionTemplate.executeWithoutResult(status ->
                        persist(order, holdToken, seatIds, tickets, startIndex, endIndex));
                return;
            } catch (SeatConflictException e) {
                LOG.warn("ORDER_CONFIRM 座位落库冲突，第 {}/{} 次 orderId={}",
                        attempt, SEAT_RETRY_TIMES, order.getId());
                if (attempt == SEAT_RETRY_TIMES) {
                    throw e;
                }
            }
        }
    }

    private void persist(Confirm_Order order,
                         String holdToken,
                         List<String> seatIds,
                         List<Confirm_OrderTicketReq> tickets,
                         int startIndex,
                         int endIndex) {
        Date now = new Date();
        for (int i = 0; i < seatIds.size(); i++) {
            Confirm_OrderTicketReq ticket = tickets.get(i);
            InventoryHoldRecord record = new InventoryHoldRecord();
            record.setId(SnowUtil.getSnowflakeNextId());
            record.setHoldToken(holdToken);
            record.setRequestId(order.getRequestId());
            record.setOrderId(order.getId());
            record.setMemberId(order.getMemberId());
            record.setPassengerId(ticket.getPassengerId());
            record.setDate(order.getDate());
            record.setTrainCode(order.getTrainCode());
            record.setSeatId(Long.valueOf(seatIds.get(i)));
            record.setSeatType(ticket.getSeatTypeCode());
            record.setStartIndex(startIndex);
            record.setEndIndex(endIndex);
            record.setStatus("H");
            record.setCreateTime(now);
            persistMapper.insertHold(record);
        }

        for (String seatId : seatIds) {
            Long id = Long.valueOf(seatId);
            SeatRecord seat = persistMapper.selectSeat(id);
            if (seat == null) {
                throw new SeatConflictException(id);
            }
            String newSell = soldSell(seat.getSell(), startIndex, endIndex);
            if (persistMapper.casSeatSell(id, newSell, seat.getVersion()) != 1) {
                throw new SeatConflictException(id);
            }
        }

        for (Map.Entry<String, Integer> entry : countBySeatType(tickets).entrySet()) {
            persistMapper.decreaseTicket(order.getDailyTrainTicketId(),
                    ticketField(entry.getKey()), entry.getValue());
        }

        persistMapper.consumeQualifications(order.getId());

        Date expireAt = new Date(now.getTime() + PAYMENT_WINDOW_MS);
        if (persistMapper.markSuccess(order.getId(), expireAt) != 1) {
            throw new IllegalStateException("订单状态已变更，无法置成功, orderId=" + order.getId());
        }
    }

    /**
     * 按本次乘车区间把 sell 对应位段置 1，得到落库后的售卖位图。
     */
    private String soldSell(String sell, int startIndex, int endIndex) {
        int from = startIndex - 1;
        int to = endIndex - 2;
        if (StrUtil.isBlank(sell) || from < 0 || to >= sell.length() || from > to) {
            throw new IllegalStateException("座位 sell 区间非法, sell=" + sell
                    + ", startIndex=" + startIndex + ", endIndex=" + endIndex);
        }
        char[] bits = sell.toCharArray();
        for (int i = from; i <= to; i++) {
            bits[i] = '1';
        }
        return new String(bits);
    }

    private Map<String, Integer> countBySeatType(List<Confirm_OrderTicketReq> tickets) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Confirm_OrderTicketReq ticket : tickets) {
            counts.merge(ticket.getSeatTypeCode(), 1, Integer::sum);
        }
        return counts;
    }

    private String ticketField(String seatTypeCode) {
        TrainCarriageTypeEnum type = TrainCarriageTypeEnum.fromCode(seatTypeCode, null);
        if (type == null) {
            throw new IllegalArgumentException("未知席别：" + seatTypeCode);
        }
        return type.name().toLowerCase();
    }

    private void releaseQuietly(Confirm_Order order,
                                String requestId,
                                List<String> seatIds,
                                List<Confirm_OrderTicketReq> tickets,
                                String seatType,
                                int startIndex,
                                int endIndex) {
        ReleaseCommand command = new ReleaseCommand();
        command.setReleaseKey("ORDER_CONFIRM:" + requestId);
        command.setOrderId(String.valueOf(order.getId()));
        command.setDate(order.getDate());
        command.setTrainCode(order.getTrainCode());
        command.setSeatType(seatType);
        command.setStartIndex(startIndex);
        command.setEndIndex(endIndex);
        List<Long> passengerIds = new ArrayList<>();
        for (Confirm_OrderTicketReq ticket : tickets) {
            passengerIds.add(ticket.getPassengerId());
        }
        command.setPassengerIds(passengerIds);
        command.setSeatIds(seatIds);
        try {
            inventoryService.release(command);
        } catch (Exception e) {
            LOG.error("ORDER_CONFIRM 座位冲突后释放失败，等待关单任务兜底 orderId={}", order.getId(), e);
        }
    }

    private void confirmRedisQuietly(Confirm_Order order, String holdToken,
                                     List<Confirm_OrderTicketReq> tickets) {
        ConfirmCommand command = new ConfirmCommand();
        command.setHoldToken(holdToken);
        command.setOrderId(String.valueOf(order.getId()));
        command.setDate(order.getDate());
        command.setTrainCode(order.getTrainCode());
        List<Long> passengerIds = new ArrayList<>();
        for (Confirm_OrderTicketReq ticket : tickets) {
            passengerIds.add(ticket.getPassengerId());
        }
        command.setPassengerIds(passengerIds);
        try {
            inventoryService.confirm(command);
        } catch (Exception e) {
            // MySQL 已提交，hold 到期会自动清理；这里只记录，避免重复消息反复失败
            LOG.warn("ORDER_CONFIRM Redis 收尾失败 orderId={} holdToken={}", order.getId(), holdToken, e);
        }
    }

    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private List<String> splitCsv(String csv) {
        List<String> values = new ArrayList<>();
        if (StrUtil.isBlank(csv)) {
            return values;
        }
        for (String item : csv.split(",")) {
            if (StrUtil.isNotBlank(item)) {
                values.add(item.trim());
            }
        }
        return values;
    }
}
