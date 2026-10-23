package com.guo.business.service;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.guo.business.TypeEnum.OrderCloseReasonEnum;
import com.guo.business.TypeEnum.TrainCarriageTypeEnum;
import com.guo.business.domain.Confirm_Order;
import com.guo.business.domain.Daily_Train_Ticket;
import com.guo.business.domain.InventoryHold;
import com.guo.business.feign.MemberFeign;
import com.guo.business.inventory.InventoryKey;
import com.guo.business.inventory.InventoryResultCode;
import com.guo.business.inventory.InventoryService;
import com.guo.business.inventory.ReleaseCommand;
import com.guo.business.inventory.ReleaseResult;
import com.guo.business.mapper.Confirm_OrderMapper;
import com.guo.business.mapper.InventoryHoldMapper;
import com.guo.business.mapper.OrderCloseMapper;
import com.guo.business.observability.TraceContext;
import com.guo.business.purchase.model.OrderOutboxRecord;
import com.guo.business.purchase.model.SeatRecord;
import com.guo.business.req.Confirm_OrderTicketReq;
import com.guo.business.waitlist.WaitlistMatchEvent;
import com.guo.business.waitlist.WaitlistMatchProducer;
import com.guo.common.exception.ExceptionConsole;
import com.guo.common.exception.ExceptionEnum;
import com.guo.common.util.SnowUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * I12 超时关闭与取消。
 *
 * <p>先落账再释放：同一本地事务内完成订单 CAS、资格/hold 释放、MySQL 账本回滚
 * （座位 sell 位与余票计数）、释放流水与 INVENTORY_RELEASED outbox；事务提交后再执行
 * {@code release.lua} 归还 Redis 库存并出队。</p>
 *
 * <p>三重幂等：订单状态 CAS、{@code inventory_release_record.uk_idempotent_key}、
 * {@code rel:{releaseKey}}。重复关闭时 CAS 返回 0 行，直接返回且不产生任何副作用。</p>
 */
@Service
public class OrderCloseService {

    private static final Logger LOG = LoggerFactory.getLogger(OrderCloseService.class);

    private static final int SEAT_RETRY_TIMES = 3;
    private static final String STATUS_CANCEL = "C";
    private static final String STATUS_CLOSED = "D";
    private static final String RELEASE_STATUS_SUCCESS = "S";
    private static final String RELEASE_STATUS_FAILED = "F";
    private static final String EVENT_TYPE_RELEASED = "INVENTORY_RELEASED";
    private static final String EVENT_TYPE_TICKET_DELETE = "TICKET_DELETE";
    private static final String RELEASE_KEY_PREFIX = "REL:";
    private static final String TICKET_STATUS_ISSUING = "I";
    private static final String TICKET_STATUS_ISSUED = "S";

    private final Confirm_OrderMapper confirmOrderMapper;
    private final OrderCloseMapper orderCloseMapper;
    private final InventoryHoldMapper inventoryHoldMapper;
    private final InventoryService inventoryService;
    private final Daily_Train_TicketService dailyTrainTicketService;
    private final MemberFeign memberFeign;
    private final OrderCloseQueue orderCloseQueue;
    private final WaitlistMatchProducer waitlistMatchProducer;
    private final StringRedisTemplate redis;
    private final TransactionTemplate transactionTemplate;

    public OrderCloseService(Confirm_OrderMapper confirmOrderMapper,
                             OrderCloseMapper orderCloseMapper,
                             InventoryHoldMapper inventoryHoldMapper,
                             InventoryService inventoryService,
                             Daily_Train_TicketService dailyTrainTicketService,
                             MemberFeign memberFeign,
                             OrderCloseQueue orderCloseQueue,
                             WaitlistMatchProducer waitlistMatchProducer,
                             StringRedisTemplate redis,
                             PlatformTransactionManager transactionManager) {
        this.confirmOrderMapper = confirmOrderMapper;
        this.orderCloseMapper = orderCloseMapper;
        this.inventoryHoldMapper = inventoryHoldMapper;
        this.inventoryService = inventoryService;
        this.dailyTrainTicketService = dailyTrainTicketService;
        this.memberFeign = memberFeign;
        this.orderCloseQueue = orderCloseQueue;
        this.waitlistMatchProducer = waitlistMatchProducer;
        this.redis = redis;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * 兜底扫描到期订单号，供 ZSet 丢数据时的定时任务使用。
     */
    public List<Long> selectExpiredOrderIds(int limit) {
        return orderCloseMapper.selectExpiredOrderIds(Math.max(limit, 1));
    }

    /**
     * 关闭订单（超时/取消/退款等）。重复关闭返回 alreadyClosed=true，不产生副作用。
     */
    public OrderCloseResult close(Long orderId, OrderCloseReasonEnum reason) {
        if (orderId == null || reason == null) {
            throw new IllegalArgumentException("关单参数不完整");
        }

        // I18：定时关单线程没有 HTTP 上下文，补一个 traceId 让释放链路日志可串联
        try (TraceContext.Scope ignored = TraceContext.openIfAbsent()) {
            CloseContext context = transactionTemplate.execute(status -> doClose(orderId, reason));
            if (context == null) {
                orderCloseQueue.cancel(orderId);
                LOG.info("订单已关闭或不存在，重复关闭无副作用 orderId={} reason={}", orderId, reason.name());
                return OrderCloseResult.alreadyClosed(orderId, reason);
            }

            // 释放阶段带上原受理 requestId，便于按 requestId 串联“受理 → 消息 → 消费 → 释放”
            try (TraceContext.Scope orderScope = TraceContext.withRequestId(context.getOrder().getRequestId())) {
                boolean redisReleased = releaseQuietly(context);
                deleteTicketsQuietly(context);
                orderCloseQueue.cancel(orderId);
                LOG.info("订单关闭完成 orderId={} reason={} seats={} redisReleased={} status={}",
                        orderId, reason.name(), context.getSeatCount(), redisReleased, context.getToStatus());
                return OrderCloseResult.closed(orderId, reason, context.getReleasedSeats(), redisReleased);
            }
        }
    }

    /**
     * 事务内完成释放落账（资格、hold、账本回滚、释放流水、outbox），不做订单状态 CAS。
     *
     * <p>退款成功链路复用该方法，由调用方在同一事务内把订单置为 R。</p>
     */
    public CloseContext releaseInTransaction(Confirm_Order order, OrderCloseReasonEnum reason) {
        return doReleaseSteps(order, reason);
    }

    /**
     * 事务提交后执行 Lua 释放与会员车票删除。
     */
    public void releaseAfterCommit(CloseContext context) {
        try (TraceContext.Scope ignored = TraceContext.withRequestId(context.getOrder().getRequestId())) {
            releaseQuietly(context);
            deleteTicketsQuietly(context);
        }
    }

    /**
     * 用户取消：校验订单归属与可关闭状态，重复取消按幂等成功返回。
     */
    public OrderCloseResult cancel(Long orderId, Long memberId) {
        Confirm_Order order = confirmOrderMapper.selectByPrimaryKey(orderId);
        if (order == null) {
            throw new ExceptionConsole(ExceptionEnum.ORDER_NOT_EXIST);
        }
        if (!order.getMemberId().equals(memberId)) {
            throw new ExceptionConsole(ExceptionEnum.ORDER_MEMBER_ERROR);
        }
        if (STATUS_CANCEL.equals(order.getStatus()) || STATUS_CLOSED.equals(order.getStatus())) {
            return OrderCloseResult.alreadyClosed(orderId, OrderCloseReasonEnum.CANCEL);
        }
        if (!closable(order.getStatus())) {
            throw new ExceptionConsole(ExceptionEnum.ORDER_STATUS_ERROR);
        }
        return close(orderId, OrderCloseReasonEnum.CANCEL);
    }

    private boolean closable(String status) {
        return "I".equals(status) || "S".equals(status) || "H".equals(status);
    }

    /**
     * 本地事务：CAS 关单 → 释放资格/hold → 回滚账本 → 释放流水 → outbox。
     */
    private CloseContext doClose(Long orderId, OrderCloseReasonEnum reason) {
        Confirm_Order order = confirmOrderMapper.selectByPrimaryKey(orderId);
        if (order == null) {
            return null;
        }
        String toStatus = OrderCloseReasonEnum.CANCEL == reason ? STATUS_CANCEL : STATUS_CLOSED;
        if (orderCloseMapper.closeOrder(orderId, toStatus, reason.name()) == 0) {
            return null;
        }
        CloseContext context = doReleaseSteps(order, reason);
        context.setToStatus(toStatus);
        return context;
    }

    private CloseContext doReleaseSteps(Confirm_Order order, OrderCloseReasonEnum reason) {
        boolean ticketDeleteRequired = ticketIssued(order.getTicketStatus());

        // hold 必须在置 R 之前读取，同时作为回滚账本与 Redis 释放的参数来源
        List<InventoryHold> holds = inventoryHoldMapper.selectHeldByOrderId(order.getId());
        rollbackLedger(order, holds);
        orderCloseMapper.releaseQualifications(order.getId());
        orderCloseMapper.releaseHolds(order.getId(), new Date());
        insertReleaseRecord(order, reason, holds.size());
        insertInventoryReleasedOutbox(order, reason, holds.size());
        if (ticketDeleteRequired) {
            insertTicketDeleteOutbox(order);
        }

        CloseContext context = new CloseContext();
        context.setOrder(order);
        context.setReason(reason);
        context.setHolds(holds);
        context.setTickets(parseTickets(order.getTickets()));
        context.setSeatCount(holds.size());
        context.setTicketDeleteRequired(ticketDeleteRequired);
        return context;
    }

    private boolean ticketIssued(String ticketStatus) {
        return TICKET_STATUS_ISSUING.equals(ticketStatus) || TICKET_STATUS_ISSUED.equals(ticketStatus);
    }

    /**
     * 回滚 MySQL 账本：清掉该订单占用的 sell 位并按席别回补余票计数。
     *
     * <p>I11 对账以 MySQL 为准，若不回滚，Redis 释放后的余票会在下一次对账被重新覆盖。</p>
     */
    private void rollbackLedger(Confirm_Order order, List<InventoryHold> holds) {
        if (holds.isEmpty()) {
            return;
        }
        Map<String, Integer> countByField = new LinkedHashMap<>();
        for (InventoryHold hold : holds) {
            clearSeatSell(hold);
            String field = ticketField(hold.getSeatType());
            if (field != null) {
                countByField.merge(field, 1, Integer::sum);
            }
        }
        if (order.getDailyTrainTicketId() == null) {
            LOG.warn("订单缺少余票行 id，跳过余票回补 orderId={}", order.getId());
            return;
        }
        for (Map.Entry<String, Integer> entry : countByField.entrySet()) {
            orderCloseMapper.increaseTicket(order.getDailyTrainTicketId(), entry.getKey(), entry.getValue());
        }
    }

    /**
     * 座位 version CAS 重试清位，失败交由 I11 对账与人工处理。
     */
    private void clearSeatSell(InventoryHold hold) {
        if (hold.getSeatId() == null || hold.getStartIndex() == null || hold.getEndIndex() == null) {
            LOG.warn("hold 缺少座位区间信息，跳过清位 holdId={} orderId={}", hold.getId(), hold.getOrderId());
            return;
        }
        for (int attempt = 1; attempt <= SEAT_RETRY_TIMES; attempt++) {
            SeatRecord seat = orderCloseMapper.selectSeat(hold.getSeatId());
            if (seat == null || StrUtil.isBlank(seat.getSell())) {
                LOG.warn("座位账本不存在或 sell 为空，跳过清位 seatId={} orderId={}",
                        hold.getSeatId(), hold.getOrderId());
                return;
            }
            String newSell = clearSell(seat.getSell(), hold.getStartIndex(), hold.getEndIndex());
            if (newSell == null) {
                return;
            }
            if (orderCloseMapper.casSeatSell(hold.getSeatId(), newSell, seat.getVersion()) == 1) {
                return;
            }
            LOG.warn("关单清位 CAS 冲突，第 {}/{} 次 seatId={} orderId={}",
                    attempt, SEAT_RETRY_TIMES, hold.getSeatId(), hold.getOrderId());
        }
        LOG.error("关单清位重试仍失败，等待对账处理 seatId={} orderId={}", hold.getSeatId(), hold.getOrderId());
    }

    private String clearSell(String sell, int startIndex, int endIndex) {
        int from = startIndex - 1;
        int to = endIndex - 2;
        if (from < 0 || to >= sell.length() || from > to) {
            LOG.warn("座位 sell 区间非法，跳过清位 sell={} startIndex={} endIndex={}", sell, startIndex, endIndex);
            return null;
        }
        char[] bits = sell.toCharArray();
        boolean changed = false;
        for (int i = from; i <= to; i++) {
            if (bits[i] == '1') {
                bits[i] = '0';
                changed = true;
            }
        }
        return changed ? new String(bits) : null;
    }

    private void insertReleaseRecord(Confirm_Order order, OrderCloseReasonEnum reason, int seatCount) {
        Date now = new Date();
        orderCloseMapper.insertReleaseRecord(
                SnowUtil.getSnowflakeNextId(),
                releaseKey(order.getId(), reason),
                order.getQualificationId() == null ? 0L : order.getQualificationId(),
                order.getId(),
                reason.getCode(),
                seatCount,
                RELEASE_STATUS_SUCCESS,
                now);
    }

    private void insertInventoryReleasedOutbox(Confirm_Order order, OrderCloseReasonEnum reason, int seatCount) {
        Date now = new Date();
        JSONObject payload = new JSONObject(true);
        payload.put("orderId", order.getId());
        payload.put("reason", reason.name());
        payload.put("date", InventoryKey.formatDate(order.getDate()));
        payload.put("trainCode", order.getTrainCode());
        payload.put("start", order.getStart());
        payload.put("end", order.getEnd());
        payload.put("seatCount", seatCount);

        OrderOutboxRecord outbox = new OrderOutboxRecord();
        outbox.setId(SnowUtil.getSnowflakeNextId());
        outbox.setEventId(EVENT_TYPE_RELEASED + ":" + order.getId() + ":" + reason.getCode());
        outbox.setAggregateType("ORDER");
        outbox.setAggregateId(order.getId());
        outbox.setEventType(EVENT_TYPE_RELEASED);
        outbox.setPayload(JSON.toJSONString(payload));
        outbox.setStatus("N");
        outbox.setRetryCount(0);
        outbox.setNextRetryAt(now);
        outbox.setCreateTime(now);
        outbox.setUpdateTime(now);
        orderCloseMapper.insertOutbox(outbox);
    }

    /**
     * 已出票订单关闭时写入 TICKET_DELETE 审计记录（docs 7.4 步骤 f）。
     */
    private void insertTicketDeleteOutbox(Confirm_Order order) {
        Date now = new Date();
        JSONObject payload = new JSONObject(true);
        payload.put("orderId", order.getId());

        OrderOutboxRecord outbox = new OrderOutboxRecord();
        outbox.setId(SnowUtil.getSnowflakeNextId());
        outbox.setEventId(EVENT_TYPE_TICKET_DELETE + ":" + order.getId());
        outbox.setAggregateType("ORDER");
        outbox.setAggregateId(order.getId());
        outbox.setEventType(EVENT_TYPE_TICKET_DELETE);
        outbox.setPayload(JSON.toJSONString(payload));
        outbox.setStatus("N");
        outbox.setRetryCount(0);
        outbox.setNextRetryAt(now);
        outbox.setCreateTime(now);
        outbox.setUpdateTime(now);
        orderCloseMapper.insertOutbox(outbox);
    }

    /**
     * 事务提交后删除会员车票；失败保留 outbox 记录（status=N）供重试与告警。
     */
    private void deleteTicketsQuietly(CloseContext context) {
        if (!context.isTicketDeleteRequired()) {
            return;
        }
        long orderId = context.getOrder().getId();
        try {
            memberFeign.deleteTicketsByOrder(orderId);
            orderCloseMapper.markOutboxSent(EVENT_TYPE_TICKET_DELETE + ":" + orderId);
            LOG.info("订单关闭后已删除会员车票 orderId={}", orderId);
        } catch (Exception e) {
            LOG.error("订单关闭后删除会员车票失败，待 outbox 重试 orderId={}", orderId, e);
        }
    }

    /**
     * 事务提交后执行 Lua 释放；失败只记录并标记流水，等待对账兜底。
     */
    private boolean releaseQuietly(CloseContext context) {
        ReleaseCommand command = buildReleaseCommand(context);
        if (command == null) {
            LOG.info("订单无可释放的 Redis 库存 orderId={}", context.getOrder().getId());
            return true;
        }
        try {
            ReleaseResult result = inventoryService.release(command);
            context.setReleasedSeats(result.getReleasedSeats());
            boolean success = result.getCode() == InventoryResultCode.OK
                    || result.getCode() == InventoryResultCode.ALREADY;
            if (success && result.getReleasedSeats() > 0) {
                publishWaitlistMatch(context, command, result.getReleasedSeats());
            }
            return success;
        } catch (Exception e) {
            LOG.error("关单 Redis 释放失败，等待对账兜底 orderId={} releaseKey={}",
                    context.getOrder().getId(), command.getReleaseKey(), e);
            markReleaseFailed(command.getReleaseKey());
            return false;
        }
    }

    /**
     * I16：真实归还库存后发布 INVENTORY_RELEASED 事件，触发同票档候补补位。
     *
     * <p>eventId 与 outbox 审计记录一致，供消费端 {@code wl:evt} 幂等去重。</p>
     */
    private void publishWaitlistMatch(CloseContext context, ReleaseCommand command, int releasedSeats) {
        Confirm_Order order = context.getOrder();
        WaitlistMatchEvent event = new WaitlistMatchEvent();
        event.setEventId(EVENT_TYPE_RELEASED + ":" + order.getId() + ":" + context.getReason().getCode());
        event.setOrderId(order.getId());
        event.setReason(context.getReason().getCode());
        event.setDate(InventoryKey.formatDate(order.getDate()));
        event.setTrainCode(order.getTrainCode());
        event.setStart(order.getStart());
        event.setEnd(order.getEnd());
        event.setSeatType(command.getSeatType());
        event.setReleasedSeats(releasedSeats);
        waitlistMatchProducer.publish(event);
    }

    private void markReleaseFailed(String releaseKey) {
        String idempotentKey = releaseKey.startsWith(RELEASE_KEY_PREFIX)
                ? releaseKey : RELEASE_KEY_PREFIX + releaseKey;
        try {
            orderCloseMapper.updateReleaseRecordStatus(idempotentKey, RELEASE_STATUS_FAILED);
        } catch (Exception e) {
            LOG.warn("释放流水状态更新失败 idempotentKey={}", idempotentKey, e);
        }
    }

    /**
     * 释放参数优先取 DB hold；INIT/PENDING 无 hold 时回退 Redis hold 凭据，最后扫描座位 hash。
     */
    private ReleaseCommand buildReleaseCommand(CloseContext context) {
        Confirm_Order order = context.getOrder();
        String releaseKey = releaseKey(order.getId(), context.getReason());

        if (!context.getHolds().isEmpty()) {
            InventoryHold first = context.getHolds().get(0);
            return command(releaseKey, order.getId(), order.getDate(), order.getTrainCode(),
                    first.getSeatType(), first.getStartIndex(), first.getEndIndex(),
                    passengerIds(context.getHolds()), seatIds(context.getHolds()));
        }

        // 预扣凭据未过期：直接使用 reserve.lua 写入的座位/资格/区间
        Map<Object, Object> hold = safeHold(String.valueOf(order.getId()));
        List<String> holdSeatIds = splitCsv(str(hold.get("seatIds")));
        List<String> holdPassengerIds = splitCsv(str(hold.get("passengerIds")));
        if (!holdSeatIds.isEmpty() && !holdPassengerIds.isEmpty()) {
            return command(releaseKey, order.getId(), order.getDate(), order.getTrainCode(),
                    seatTypeCode(str(hold.get("seatType"))),
                    parseInt(str(hold.get("startIndex"))), parseInt(str(hold.get("endIndex"))),
                    holdPassengerIds, holdSeatIds);
        }

        // DB hold 与 Redis 凭据都不存在：座位账本已丢失，跳过 Redis 座位释放。
        // 原实现用 KEYS + 全量 HGETALL 反查座位，单次关单要扫 2 万+ 座位 key（slowlog 实测 88ms/次），
        // Redis 单线程会被打满，进而拖垮受理链路；残留座位 hash 交给库存对账按“期望无占用”清理。
        LOG.warn("订单无 hold 账本与凭据，跳过 Redis 座位释放 orderId={}", order.getId());
        return null;
    }

    private ReleaseCommand command(String releaseKey, Long orderId, Date date, String trainCode,
                                   String seatType, Integer startIndex, Integer endIndex,
                                   List<String> passengerIds, List<String> seatIds) {
        if (StrUtil.isBlank(seatType) || startIndex == null || endIndex == null
                || startIndex <= 0 || endIndex <= startIndex
                || passengerIds.isEmpty() || seatIds.isEmpty()) {
            return null;
        }
        ReleaseCommand command = new ReleaseCommand();
        command.setReleaseKey(releaseKey);
        command.setOrderId(String.valueOf(orderId));
        command.setDate(date);
        command.setTrainCode(trainCode);
        command.setSeatType(seatType);
        command.setStartIndex(startIndex);
        command.setEndIndex(endIndex);
        List<Long> passengers = new ArrayList<>(passengerIds.size());
        for (String passengerId : passengerIds) {
            passengers.add(Long.valueOf(passengerId));
        }
        command.setPassengerIds(passengers);
        command.setSeatIds(seatIds);
        return command;
    }

    /**
     * reserve.lua 写入的 hold 凭据 key 固定为 HOLD-{orderId}。
     */
    private Map<Object, Object> safeHold(String orderId) {
        try {
            Map<Object, Object> hold = redis.opsForHash().entries(InventoryKey.hold("HOLD-" + orderId));
            if (hold == null || hold.isEmpty() || !orderId.equals(str(hold.get("orderId")))) {
                return Collections.emptyMap();
            }
            return hold;
        } catch (Exception e) {
            LOG.warn("读取 hold 凭据失败 orderId={}", orderId, e);
            return Collections.emptyMap();
        }
    }

    private List<String> passengerIds(List<InventoryHold> holds) {
        List<String> passengerIds = new ArrayList<>(holds.size());
        for (InventoryHold hold : holds) {
            passengerIds.add(String.valueOf(hold.getPassengerId()));
        }
        return passengerIds;
    }

    private List<String> seatIds(List<InventoryHold> holds) {
        List<String> seatIds = new ArrayList<>(holds.size());
        for (InventoryHold hold : holds) {
            seatIds.add(String.valueOf(hold.getSeatId()));
        }
        return seatIds;
    }

    private List<String> passengerIdsFromTickets(List<Confirm_OrderTicketReq> tickets) {
        List<String> passengerIds = new ArrayList<>(tickets.size());
        for (Confirm_OrderTicketReq ticket : tickets) {
            if (ticket.getPassengerId() != null) {
                passengerIds.add(String.valueOf(ticket.getPassengerId()));
            }
        }
        return passengerIds;
    }

    private List<Confirm_OrderTicketReq> parseTickets(String ticketsJson) {
        if (StrUtil.isBlank(ticketsJson)) {
            return Collections.emptyList();
        }
        try {
            List<Confirm_OrderTicketReq> tickets =
                    JSON.parseArray(ticketsJson, Confirm_OrderTicketReq.class);
            return tickets == null ? Collections.emptyList() : tickets;
        } catch (Exception e) {
            LOG.warn("订单车票明细解析失败，跳过依赖票面的释放信息", e);
            return Collections.emptyList();
        }
    }

    private String releaseKey(Long orderId, OrderCloseReasonEnum reason) {
        return RELEASE_KEY_PREFIX + orderId + ":" + reason.getCode();
    }

    private String ticketField(String seatType) {
        try {
            TrainCarriageTypeEnum type = TrainCarriageTypeEnum.fromCode(seatType, null);
            return type == null ? null : type.name().toLowerCase();
        } catch (IllegalArgumentException e) {
            LOG.warn("未知席别，跳过余票回补 seatType={}", seatType);
            return null;
        }
    }

    /**
     * hold 凭据里存的是 ydz/edz/rw/yw，转回席别编码。
     */
    private String seatTypeCode(String field) {
        if (StrUtil.isBlank(field)) {
            return null;
        }
        for (TrainCarriageTypeEnum type : TrainCarriageTypeEnum.values()) {
            if (type.name().equalsIgnoreCase(field)) {
                return type.getCode();
            }
        }
        return field;
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static List<String> splitCsv(String csv) {
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

    /**
     * 一次关单的上下文：事务内组装，提交后用于 Redis 释放。
     */
    public static class CloseContext {

        private Confirm_Order order;
        private OrderCloseReasonEnum reason;
        private String toStatus;
        private List<InventoryHold> holds;
        private List<Confirm_OrderTicketReq> tickets;
        private int seatCount;
        private int releasedSeats;
        private boolean ticketDeleteRequired;

        public Confirm_Order getOrder() {
            return order;
        }

        public void setOrder(Confirm_Order order) {
            this.order = order;
        }

        public OrderCloseReasonEnum getReason() {
            return reason;
        }

        public void setReason(OrderCloseReasonEnum reason) {
            this.reason = reason;
        }

        public String getToStatus() {
            return toStatus;
        }

        public void setToStatus(String toStatus) {
            this.toStatus = toStatus;
        }

        public List<InventoryHold> getHolds() {
            return holds;
        }

        public void setHolds(List<InventoryHold> holds) {
            this.holds = holds;
        }

        public List<Confirm_OrderTicketReq> getTickets() {
            return tickets;
        }

        public void setTickets(List<Confirm_OrderTicketReq> tickets) {
            this.tickets = tickets;
        }

        public int getSeatCount() {
            return seatCount;
        }

        public void setSeatCount(int seatCount) {
            this.seatCount = seatCount;
        }

        public int getReleasedSeats() {
            return releasedSeats;
        }

        public void setReleasedSeats(int releasedSeats) {
            this.releasedSeats = releasedSeats;
        }

        public boolean isTicketDeleteRequired() {
            return ticketDeleteRequired;
        }

        public void setTicketDeleteRequired(boolean ticketDeleteRequired) {
            this.ticketDeleteRequired = ticketDeleteRequired;
        }
    }
}
