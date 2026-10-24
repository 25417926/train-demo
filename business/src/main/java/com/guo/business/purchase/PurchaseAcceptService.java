package com.guo.business.purchase;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.alibaba.fastjson.JSON;
import com.guo.business.TypeEnum.TrainCarriageTypeEnum;
import com.guo.business.domain.Daily_Train_Ticket;
import com.guo.business.inventory.InventoryKey;
import com.guo.business.inventory.InventoryResultCode;
import com.guo.business.inventory.InventoryService;
import com.guo.business.inventory.ReleaseCommand;
import com.guo.business.inventory.ReserveCommand;
import com.guo.business.inventory.ReserveResult;
import com.guo.business.metrics.PurchaseAcceptMetrics;
import com.guo.business.observability.TraceContext;
import com.guo.business.req.Confirm_OrderTicketReq;
import com.guo.business.service.Daily_Train_TicketService;
import com.guo.business.service.OrderCloseQueue;
import com.guo.business.waitlist.WaitlistKey;
import com.guo.common.util.SnowUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 受理链路（I06）：幂等判断 → Lua 预扣 → 订单 INIT + 事务消息 → 失败回滚。
 *
 * <p>正确性依赖三层：purchase:req 原子幂等、purchase_qualification.uk_active、
 * confirm_order.uk_request_id。Redis 只是快路径，MySQL 唯一键是最终防线。</p>
 */
@Service
public class PurchaseAcceptService {

    private static final Logger LOG = LoggerFactory.getLogger(PurchaseAcceptService.class);

    /** Idempotency-Key：8~64 位字母、数字、下划线或中划线（UUID 合法） */
    private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("[A-Za-z0-9_-]{8,64}");

    /** 受理后出票窗口 5 分钟，与 PurchaseTxWriter 的 expire_at 对齐 */
    private static final long ORDER_EXPIRE_MS = 5 * 60 * 1000L;

    private final InventoryService inventoryService;
    private final Daily_Train_TicketService dailyTrainTicketService;
    private final PurchaseTxWriter purchaseTxWriter;
    private final PurchaseConfirmProducer purchaseConfirmProducer;
    private final OrderCloseQueue orderCloseQueue;
    private final StringRedisTemplate redis;
    private final PurchaseAcceptMetrics metrics;

    public PurchaseAcceptService(InventoryService inventoryService,
                                 Daily_Train_TicketService dailyTrainTicketService,
                                 PurchaseTxWriter purchaseTxWriter,
                                 PurchaseConfirmProducer purchaseConfirmProducer,
                                 OrderCloseQueue orderCloseQueue,
                                 StringRedisTemplate redis,
                                 PurchaseAcceptMetrics metrics) {
        this.inventoryService = inventoryService;
        this.dailyTrainTicketService = dailyTrainTicketService;
        this.purchaseTxWriter = purchaseTxWriter;
        this.purchaseConfirmProducer = purchaseConfirmProducer;
        this.orderCloseQueue = orderCloseQueue;
        this.redis = redis;
        this.metrics = metrics;
    }

    public PurchaseApplyResp accept(PurchaseApplyReq req, Long memberId, String idempotencyKey) {
        long begin = System.nanoTime();
        try {
            String requestId = validateIdempotencyKey(idempotencyKey);
            // I18：受理线程绑定 traceId/requestId，事务消息与消费日志沿用同一链路
            try (TraceContext.Scope ignored = TraceContext.open(TraceContext.currentTraceId(), requestId)) {
                return doAccept(req, memberId, requestId, begin);
            }
        } catch (PurchaseException e) {
            metrics.recordRejected(e.getErrorCode().name(), begin);
            throw e;
        } catch (RuntimeException e) {
            metrics.recordRejected("UNEXPECTED", begin);
            throw e;
        }
    }

    private PurchaseApplyResp doAccept(PurchaseApplyReq req, Long memberId, String requestId, long begin) {
        if (req == null || memberId == null) {
            throw new PurchaseException(PurchaseErrorCode.PARAM_INVALID, "受理参数不完整");
        }
        List<Confirm_OrderTicketReq> tickets = req.getTickets();
        String seatType = validateTickets(tickets);
        String dateText = InventoryKey.formatDate(req.getDate());

        // 1. 幂等快判：同一 Idempotency-Key 已受理则直接返回已有订单
        PurchaseApplyResp replay = findByRedis(requestId);
        if (replay != null) {
            LOG.info("受理幂等命中 requestId={} orderId={}", requestId, replay.getOrderId());
            metrics.recordDuplicate(begin);
            return replay;
        }

        // 2. 余票行与站序
        Daily_Train_Ticket dailyTrainTicket = dailyTrainTicketService.selectByUnique(
                req.getDate(), req.getTrainCode(), req.getStart(), req.getEnd());
        if (dailyTrainTicket == null
                || dailyTrainTicket.getStartIndex() == null
                || dailyTrainTicket.getEndIndex() == null
                || dailyTrainTicket.getEndIndex() <= dailyTrainTicket.getStartIndex()) {
            throw new PurchaseException(PurchaseErrorCode.TICKET_NOT_EXIST);
        }
        int startIndex = dailyTrainTicket.getStartIndex();
        int endIndex = dailyTrainTicket.getEndIndex();

        // 3. 候补优先：票档只要还有候补在排队，普通下单一律拒绝，余票交给匹配器消费
        if (waitlistBlocked(dateText, req, seatType)) {
            throw new PurchaseException(PurchaseErrorCode.WAITLIST_PRIORITY);
        }

        // 4. Lua 预扣：资格 + 余票 + 选座 + hold 凭据，一次原子完成
        long orderId = SnowUtil.getSnowflakeNextId();
        ReserveResult reserved = inventoryService.reserve(reserveCommand(
                requestId, orderId, req, seatType, startIndex, endIndex, tickets));
        if (reserved.getCode() == InventoryResultCode.DUPLICATE) {
            metrics.recordDuplicate(begin);
            return existingOrder(requestId, reserved.getOrderId());
        }
        if (reserved.getCode() == InventoryResultCode.NO_TICKET) {
            throw new PurchaseException(PurchaseErrorCode.TICKET_NOT_ENOUGH);
        }
        if (reserved.getCode() == InventoryResultCode.NOT_READY) {
            throw new PurchaseException(PurchaseErrorCode.INVENTORY_NOT_READY);
        }
        if (reserved.getCode() != InventoryResultCode.OK) {
            throw new PurchaseException(PurchaseErrorCode.ACCEPT_FAILED,
                    "库存预扣失败：" + reserved.getCode());
        }

        // 5. 事务消息：本地事务写四张表，成功才 COMMIT
        PurchaseAcceptContext context = buildContext(
                requestId, orderId, memberId, req, dateText, seatType, startIndex, endIndex, tickets, reserved);
        PurchaseSendResult sendResult = purchaseConfirmProducer.send(context);
        if (!sendResult.isAccepted()) {
            rollback(context);
            Long committedOrderId = purchaseTxWriter.findOrderId(requestId);
            if (committedOrderId != null) {
                LOG.warn("受理事务消息未确认但本地事务已提交 requestId={} orderId={}",
                        requestId, committedOrderId);
                scheduleClose(committedOrderId);
                metrics.recordSuccess(begin);
                return PurchaseApplyResp.accepted(requestId, committedOrderId);
            }
            throw new PurchaseException(PurchaseErrorCode.ACCEPT_FAILED);
        }
        scheduleClose(orderId);
        metrics.recordSuccess(begin);
        return PurchaseApplyResp.accepted(requestId, orderId);
    }

    /**
     * 受理成功即进入超时关闭窗口，5 分钟后由调度任务兜底关单。
     */
    private void scheduleClose(long orderId) {
        orderCloseQueue.schedule(orderId, new Date(System.currentTimeMillis() + ORDER_EXPIRE_MS));
    }

    private String validateIdempotencyKey(String idempotencyKey) {
        if (StrUtil.isBlank(idempotencyKey)) {
            throw new PurchaseException(PurchaseErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }
        String key = idempotencyKey.trim();
        if (!IDEMPOTENCY_KEY_PATTERN.matcher(key).matches()) {
            throw new PurchaseException(PurchaseErrorCode.IDEMPOTENCY_KEY_INVALID);
        }
        return key;
    }

    private String validateTickets(List<Confirm_OrderTicketReq> tickets) {
        if (tickets == null || tickets.isEmpty()) {
            throw new PurchaseException(PurchaseErrorCode.PARAM_INVALID, "车票参数不能为空");
        }
        String seatType = tickets.get(0).getSeatTypeCode();
        Set<Long> passengerIds = new HashSet<>();
        for (Confirm_OrderTicketReq ticket : tickets) {
            if (ticket == null || ticket.getPassengerId() == null || StrUtil.isBlank(ticket.getSeatTypeCode())) {
                throw new PurchaseException(PurchaseErrorCode.PARAM_INVALID, "车票缺少乘客或席别");
            }
            if (!passengerIds.add(ticket.getPassengerId())) {
                throw new PurchaseException(PurchaseErrorCode.PARAM_INVALID, "同一乘客不能重复购票");
            }
            if (!ticket.getSeatTypeCode().equals(seatType)) {
                throw new PurchaseException(PurchaseErrorCode.PARAM_INVALID, "同一订单席别必须一致");
            }
        }
        try {
            TrainCarriageTypeEnum.fromCode(seatType, null);
        } catch (IllegalArgumentException e) {
            throw new PurchaseException(PurchaseErrorCode.PARAM_INVALID, "未知席别：" + seatType);
        }
        return seatType;
    }

    private PurchaseApplyResp findByRedis(String requestId) {
        Object orderId = redis.opsForHash().get(InventoryKey.purchaseRequest(requestId), "orderId");
        if (orderId == null || StrUtil.isBlank(String.valueOf(orderId))) {
            return null;
        }
        return PurchaseApplyResp.accepted(requestId, Long.valueOf(String.valueOf(orderId)));
    }

    /**
     * DUPLICATE 且 Redis 无法给出订单号时（如快要过期），回查 MySQL 唯一键。
     */
    private PurchaseApplyResp existingOrder(String requestId, String redisOrderId) {
        if (StrUtil.isNotBlank(redisOrderId)) {
            return PurchaseApplyResp.accepted(requestId, Long.valueOf(redisOrderId));
        }
        Long orderId = purchaseTxWriter.findOrderId(requestId);
        if (orderId != null) {
            return PurchaseApplyResp.accepted(requestId, orderId);
        }
        throw new PurchaseException(PurchaseErrorCode.DUPLICATE_PURCHASE);
    }

    /**
     * 候补优先准入：该票档只要还有候补在排队，普通下单就拒绝。
     *
     * <p>I16 初版只在"余票不足以满足本单"时才拦，释放回补余票后闸门随即失效，
     * 刚释放的座位会被普通下单先抢走，形成候补与普通并行。这里改为按排队数准入：
     * 释放出来的余票只由候补匹配器消费，队列清空后普通下单自动恢复。</p>
     */
    private boolean waitlistBlocked(String dateText, PurchaseApplyReq req, String seatType) {
        String waitlistKey = WaitlistKey.count(
                WaitlistKey.bucket(dateText, req.getTrainCode(), req.getStart(), req.getEnd(), seatType));
        Object waiting = redis.opsForValue().get(waitlistKey);
        return waiting != null && Long.parseLong(String.valueOf(waiting)) > 0;
    }

    private ReserveCommand reserveCommand(String requestId, long orderId, PurchaseApplyReq req,
                                          String seatType, int startIndex, int endIndex,
                                          List<Confirm_OrderTicketReq> tickets) {
        ReserveCommand command = new ReserveCommand();
        command.setRequestId(requestId);
        command.setOrderId(String.valueOf(orderId));
        command.setDate(req.getDate());
        command.setTrainCode(req.getTrainCode());
        command.setSeatType(seatType);
        command.setStartIndex(startIndex);
        command.setEndIndex(endIndex);
        List<Long> passengerIds = new ArrayList<>();
        for (Confirm_OrderTicketReq ticket : tickets) {
            passengerIds.add(ticket.getPassengerId());
        }
        command.setPassengerIds(passengerIds);
        return command;
    }

    private PurchaseAcceptContext buildContext(String requestId, long orderId, Long memberId,
                                               PurchaseApplyReq req, String dateText, String seatType,
                                               int startIndex, int endIndex,
                                               List<Confirm_OrderTicketReq> tickets,
                                               ReserveResult reserved) {
        PurchaseAcceptContext context = new PurchaseAcceptContext();
        context.setRequestId(requestId);
        context.setOrderId(orderId);
        context.setMemberId(memberId);
        context.setDateText(dateText);
        context.setTrainCode(req.getTrainCode());
        context.setStart(req.getStart());
        context.setEnd(req.getEnd());
        context.setSeatType(seatType);
        context.setDailyTrainTicketId(req.getDailyTrainTicketId());
        context.setStartIndex(startIndex);
        context.setEndIndex(endIndex);
        context.setHoldToken(StrUtil.isBlank(reserved.getHoldToken())
                ? "HOLD-" + orderId : reserved.getHoldToken());
        List<Long> seatIds = new ArrayList<>();
        for (String seatId : reserved.getSeatIds()) {
            seatIds.add(Long.valueOf(seatId));
        }
        context.setSeatIds(seatIds);
        List<Long> passengerIds = new ArrayList<>();
        for (Confirm_OrderTicketReq ticket : tickets) {
            passengerIds.add(ticket.getPassengerId());
        }
        context.setPassengerIds(passengerIds);
        context.setTickets(JSON.toJSONString(tickets));
        context.setPayloadHash(DigestUtil.sha256Hex(JSON.toJSONString(req)));
        return context;
    }

    /**
     * 失败回滚：先归还库存（release.lua 幂等），再清理 hold 与幂等状态。
     */
    private void rollback(PurchaseAcceptContext context) {
        ReleaseCommand release = new ReleaseCommand();
        // I19 演练暴露：releaseKey 若只按 requestId 生成，同键重试会命中上一次的释放标记，
        // 导致本次预扣的资格/座位不被释放，后续重试永久返回 DUPLICATE_PURCHASE。
        // 每次受理的 orderId 唯一，用它区分到具体一次预扣。
        release.setReleaseKey("ACCEPT-" + context.getRequestId() + "-" + context.getOrderId());
        release.setOrderId(String.valueOf(context.getOrderId()));
        release.setDate(cn.hutool.core.date.DateUtil.parse(context.getDateText()));
        release.setTrainCode(context.getTrainCode());
        release.setSeatType(context.getSeatType());
        release.setStartIndex(context.getStartIndex());
        release.setEndIndex(context.getEndIndex());
        release.setPassengerIds(context.getPassengerIds());
        List<String> seatIds = new ArrayList<>();
        for (Long seatId : context.getSeatIds()) {
            seatIds.add(String.valueOf(seatId));
        }
        release.setSeatIds(seatIds);
        try {
            inventoryService.release(release);
        } catch (Exception e) {
            LOG.error("受理失败回滚库存异常 requestId={} orderId={}",
                    context.getRequestId(), context.getOrderId(), e);
        }
        try {
            inventoryService.abort(context.getRequestId(), String.valueOf(context.getOrderId()),
                    context.getHoldToken());
        } catch (Exception e) {
            LOG.error("受理失败清理幂等状态异常 requestId={} orderId={}",
                    context.getRequestId(), context.getOrderId(), e);
        }
    }
}
