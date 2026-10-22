package com.guo.business.purchase;

import cn.hutool.core.date.DateUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.guo.business.TypeEnum.ConfirmOrderStatusEnum;
import com.guo.business.domain.Confirm_Order;
import com.guo.business.mapper.PurchaseAcceptMapper;
import com.guo.business.purchase.model.OrderOutboxRecord;
import com.guo.business.purchase.model.PurchaseQualificationRecord;
import com.guo.business.purchase.model.PurchaseRequestRecord;
import com.guo.common.util.SnowUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Date;

/**
 * 受理本地事务（I06）：一次提交四张表，任一失败整体回滚。
 */
@Service
public class PurchaseTxWriter {

    private static final Logger LOG = LoggerFactory.getLogger(PurchaseTxWriter.class);

    /** 资格占位 20 分钟，与 reserve.lua 的 qual TTL 对齐 */
    private static final int QUALIFICATION_TTL_MINUTES = 20;
    /** 受理后出票窗口 5 分钟 */
    private static final int ORDER_EXPIRE_MINUTES = 5;

    private static final String QUALIFICATION_STATUS_HELD = "H";
    private static final String REQUEST_STATUS_ACCEPTED = "A";
    private static final String OUTBOX_STATUS_NEW = "N";
    private static final String EVENT_TYPE_ORDER_CONFIRM = "ORDER_CONFIRM";

    private final PurchaseAcceptMapper purchaseAcceptMapper;
    private final TransactionTemplate transactionTemplate;

    public PurchaseTxWriter(PurchaseAcceptMapper purchaseAcceptMapper,
                            PlatformTransactionManager transactionManager) {
        this.purchaseAcceptMapper = purchaseAcceptMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * 本地事务：资格 → 请求 → 订单 INIT → Outbox。
     */
    public void persist(PurchaseAcceptContext ctx) {
        transactionTemplate.executeWithoutResult(status -> doPersist(ctx));
    }

    public boolean existsRequest(String requestId) {
        return purchaseAcceptMapper.countByRequestId(requestId) > 0;
    }

    /**
     * 幂等兜底：按 request_id 回查已受理的订单号。
     */
    public Long findOrderId(String requestId) {
        return purchaseAcceptMapper.selectOrderIdByRequestId(requestId);
    }

    private void doPersist(PurchaseAcceptContext ctx) {
        Date now = new Date();
        Date qualificationExpireAt = DateUtil.offsetMinute(now, QUALIFICATION_TTL_MINUTES);

        long firstQualificationId = 0L;
        for (Long passengerId : ctx.getPassengerIds()) {
            PurchaseQualificationRecord qualification = new PurchaseQualificationRecord();
            long id = SnowUtil.getSnowflakeNextId();
            if (firstQualificationId == 0L) {
                firstQualificationId = id;
            }
            qualification.setId(id);
            qualification.setRequestId(ctx.getRequestId());
            qualification.setMemberId(ctx.getMemberId());
            qualification.setPassengerId(passengerId);
            qualification.setDate(java.sql.Date.valueOf(ctx.getDateText()));
            qualification.setTrainCode(ctx.getTrainCode());
            qualification.setStart(ctx.getStart());
            qualification.setEnd(ctx.getEnd());
            qualification.setSeatType(ctx.getSeatType());
            qualification.setOrderId(ctx.getOrderId());
            qualification.setStatus(QUALIFICATION_STATUS_HELD);
            qualification.setActiveKey(activeKey(ctx, passengerId));
            qualification.setExpireAt(qualificationExpireAt);
            qualification.setCreateTime(now);
            qualification.setUpdateTime(now);
            purchaseAcceptMapper.insertQualification(qualification);
        }

        PurchaseRequestRecord request = new PurchaseRequestRecord();
        request.setId(SnowUtil.getSnowflakeNextId());
        request.setRequestId(ctx.getRequestId());
        request.setMemberId(ctx.getMemberId());
        request.setPayloadHash(ctx.getPayloadHash());
        request.setStatus(REQUEST_STATUS_ACCEPTED);
        request.setOrderId(ctx.getOrderId());
        request.setQualificationId(firstQualificationId);
        request.setCreateTime(now);
        request.setUpdateTime(now);
        purchaseAcceptMapper.insertPurchaseRequest(request);

        Confirm_Order order = new Confirm_Order();
        order.setId(ctx.getOrderId());
        order.setMemberId(ctx.getMemberId());
        order.setDate(DateUtil.parse(ctx.getDateText()));
        order.setTrainCode(ctx.getTrainCode());
        order.setStart(ctx.getStart());
        order.setEnd(ctx.getEnd());
        order.setDailyTrainTicketId(ctx.getDailyTrainTicketId());
        order.setStatus(ConfirmOrderStatusEnum.INIT.getCode());
        order.setTickets(ctx.getTickets());
        order.setCreateTime(now);
        order.setUpdateTime(now);
        order.setRequestId(ctx.getRequestId());
        order.setQualificationId(firstQualificationId);
        order.setExpireAt(DateUtil.offsetMinute(now, ORDER_EXPIRE_MINUTES));
        order.setTicketStatus("N");
        order.setVersion(0);
        purchaseAcceptMapper.insertInitOrder(order);

        OrderOutboxRecord outbox = new OrderOutboxRecord();
        outbox.setId(SnowUtil.getSnowflakeNextId());
        outbox.setEventId(EVENT_TYPE_ORDER_CONFIRM + ":" + ctx.getRequestId());
        outbox.setAggregateType("ORDER");
        outbox.setAggregateId(ctx.getOrderId());
        outbox.setEventType(EVENT_TYPE_ORDER_CONFIRM);
        outbox.setPayload(outboxPayload(ctx));
        outbox.setStatus(OUTBOX_STATUS_NEW);
        outbox.setRetryCount(0);
        outbox.setNextRetryAt(now);
        outbox.setCreateTime(now);
        outbox.setUpdateTime(now);
        purchaseAcceptMapper.insertOutbox(outbox);

        LOG.info("受理本地事务提交 requestId={} orderId={} passengers={}",
                ctx.getRequestId(), ctx.getOrderId(), ctx.getPassengerIds().size());
    }

    private String activeKey(PurchaseAcceptContext ctx, Long passengerId) {
        return "PQ:" + ctx.getDateText() + ":" + ctx.getTrainCode() + ":" + passengerId;
    }

    private String outboxPayload(PurchaseAcceptContext ctx) {
        JSONObject payload = new JSONObject(true);
        payload.put("orderId", ctx.getOrderId());
        payload.put("requestId", ctx.getRequestId());
        payload.put("holdToken", ctx.getHoldToken());
        payload.put("seatIds", ctx.getSeatIds());
        return JSON.toJSONString(payload);
    }
}
