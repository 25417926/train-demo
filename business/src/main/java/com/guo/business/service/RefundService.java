package com.guo.business.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.guo.business.TypeEnum.OrderCloseReasonEnum;
import com.guo.business.TypeEnum.RefundStatusEnum;
import com.guo.business.domain.Confirm_Order;
import com.guo.business.domain.Payment;
import com.guo.business.domain.Refund;
import com.guo.business.mapper.Confirm_OrderMapper;
import com.guo.business.mapper.PaymentMapper;
import com.guo.business.mapper.RefundMapper;
import com.guo.business.req.RefundReq;
import com.guo.business.resp.RefundResp;
import com.guo.common.exception.ExceptionConsole;
import com.guo.common.exception.ExceptionEnum;
import com.guo.common.req.PageReq;
import com.guo.common.resp.PageResp;
import com.guo.common.util.SnowUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Date;
import java.util.List;

/**
 * I13 退款两阶段。
 *
 * <p>阶段一：订单 Y→G 并把退款流水置为处理中（幂等键 {@code REFUND:{orderId}}），
 * 此时不释放任何库存与资格；阶段二成功才在同一事务内把订单置 R、写退款成功、
 * 复用关单释放落账，提交后执行 {@code release.lua} 与会员车票删除；
 * 阶段二失败则订单回退为 Y、流水置失败，库存与资格保持不变。</p>
 */
@Service
public class RefundService {

    private static final Logger LOG = LoggerFactory.getLogger(RefundService.class);

    private static final String STATUS_PAID = "Y";
    private static final String STATUS_REFUNDING = "G";
    private static final String STATUS_REFUNDED = "R";
    private static final String TICKET_STATUS_ISSUED = "S";
    private static final String TICKET_STATUS_NONE = "N";
    private static final String REFUND_KEY_PREFIX = "REFUND:";
    private static final int FAIL_REASON_MAX_LENGTH = 500;

    private final RefundMapper refundMapper;
    private final PaymentMapper paymentMapper;
    private final Confirm_OrderMapper confirmOrderMapper;
    private final PaymentService paymentService;
    private final OrderCloseService orderCloseService;
    private final TransactionTemplate transactionTemplate;

    public RefundService(RefundMapper refundMapper,
                         PaymentMapper paymentMapper,
                         Confirm_OrderMapper confirmOrderMapper,
                         PaymentService paymentService,
                         OrderCloseService orderCloseService,
                         PlatformTransactionManager transactionManager) {
        this.refundMapper = refundMapper;
        this.paymentMapper = paymentMapper;
        this.confirmOrderMapper = confirmOrderMapper;
        this.paymentService = paymentService;
        this.orderCloseService = orderCloseService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public RefundResp refund(RefundReq req, Long memberId) {
        Confirm_Order order = loadOrder(req.getOrderId(), memberId);
        String idempotentKey = REFUND_KEY_PREFIX + order.getId();

        // 已退款/退款中：幂等返回已有流水，不重复释放
        if (STATUS_REFUNDED.equals(order.getStatus()) || STATUS_REFUNDING.equals(order.getStatus())) {
            return existingRefund(order, idempotentKey);
        }
        if (!STATUS_PAID.equals(order.getStatus())) {
            throw new ExceptionConsole(ExceptionEnum.ORDER_STATUS_ERROR);
        }
        if (!TICKET_STATUS_ISSUED.equals(order.getTicketStatus())) {
            throw new ExceptionConsole(ExceptionEnum.TICKET_ISSUING);
        }
        Payment payment = paymentMapper.selectByOrderId(order.getId());
        if (ObjectUtil.isNull(payment)) {
            throw new ExceptionConsole(ExceptionEnum.PAYMENT_NOT_EXIST);
        }

        Refund refund = buildRefunding(order, payment, req, idempotentKey);
        if (!prepareRefunding(order.getId(), refund)) {
            // 并发下订单已被其他请求置为 G/R，返回对方的结果
            return existingRefund(loadOrder(order.getId(), memberId), idempotentKey);
        }

        try {
            RefundResp resp = finalizeSuccess(order, payment, refund);
            LOG.info("订单[{}]退款完成", order.getId());
            return resp;
        } catch (RuntimeException e) {
            LOG.error("订单[{}]退款失败，回滚订单与退款流水", order.getId(), e);
            finalizeFailure(order.getId(), idempotentKey, e);
            throw e;
        }
    }

    private Confirm_Order loadOrder(Long orderId, Long memberId) {
        Confirm_Order order = confirmOrderMapper.selectByPrimaryKey(orderId);
        if (ObjectUtil.isNull(order)) {
            throw new ExceptionConsole(ExceptionEnum.ORDER_NOT_EXIST);
        }
        if (!order.getMemberId().equals(memberId)) {
            throw new ExceptionConsole(ExceptionEnum.ORDER_MEMBER_ERROR);
        }
        return order;
    }

    private RefundResp existingRefund(Confirm_Order order, String idempotentKey) {
        Refund existing = refundMapper.selectByIdempotentKey(idempotentKey);
        if (existing != null) {
            LOG.info("订单[{}]重复退款申请，返回已有流水 status={}", order.getId(), existing.getRefundStatus());
            return BeanUtil.copyProperties(existing, RefundResp.class);
        }
        if (STATUS_REFUNDING.equals(order.getStatus())) {
            throw new ExceptionConsole(ExceptionEnum.ORDER_STATUS_ERROR);
        }
        throw new ExceptionConsole(ExceptionEnum.REFUND_ALREADY_DONE);
    }

    private Refund buildRefunding(Confirm_Order order, Payment payment, RefundReq req, String idempotentKey) {
        Date now = new Date();
        Refund refund = new Refund();
        refund.setId(SnowUtil.getSnowflakeNextId());
        refund.setOrderId(order.getId());
        refund.setPaymentId(payment.getId());
        refund.setMemberId(order.getMemberId());
        refund.setAmount(payment.getAmount());
        refund.setReason(req.getReason());
        refund.setIdempotentKey(idempotentKey);
        refund.setRefundStatus(RefundStatusEnum.INIT.getCode());
        refund.setCreateTime(now);
        refund.setUpdateTime(now);
        return refund;
    }

    /**
     * 阶段一：订单 Y→G 与 REFUNDING 流水同一事务，失败返回 false（并发已被处理）。
     */
    private boolean prepareRefunding(Long orderId, Refund refund) {
        Boolean prepared = transactionTemplate.execute(status -> {
            if (confirmOrderMapper.updateStatusCas(orderId, STATUS_PAID, STATUS_REFUNDING, new Date()) != 1) {
                return false;
            }
            refundMapper.insertOrResetRefunding(refund);
            return true;
        });
        return Boolean.TRUE.equals(prepared);
    }

    /**
     * 阶段二成功：订单 G→R、退款成功、释放落账同一事务，提交后 Lua 释放与删票。
     */
    private RefundResp finalizeSuccess(Confirm_Order order, Payment payment, Refund refund) {
        Date now = new Date();
        OrderCloseService.CloseContext context = transactionTemplate.execute(status -> {
            if (confirmOrderMapper.updateStatusAndTicketCas(order.getId(), STATUS_REFUNDING,
                    STATUS_REFUNDED, TICKET_STATUS_NONE, now) != 1) {
                throw new IllegalStateException("订单状态已变更，无法完成退款, orderId=" + order.getId());
            }
            paymentService.markRefunded(payment.getId());
            if (refundMapper.markSuccess(refund.getIdempotentKey(), now, now) != 1) {
                throw new IllegalStateException("退款流水状态已变更, orderId=" + order.getId());
            }
            return orderCloseService.releaseInTransaction(order, OrderCloseReasonEnum.REFUND);
        });

        orderCloseService.releaseAfterCommit(context);

        Refund latest = refundMapper.selectByIdempotentKey(refund.getIdempotentKey());
        if (latest == null) {
            refund.setRefundStatus(RefundStatusEnum.SUCCESS.getCode());
            refund.setRefundTime(now);
            latest = refund;
        }
        return BeanUtil.copyProperties(latest, RefundResp.class);
    }

    /**
     * 阶段二失败：订单 G→Y、流水置失败，库存与资格保持原状。
     */
    private void finalizeFailure(Long orderId, String idempotentKey, RuntimeException cause) {
        Date now = new Date();
        try {
            transactionTemplate.executeWithoutResult(status -> {
                confirmOrderMapper.updateStatusAndTicketCas(orderId, STATUS_REFUNDING, STATUS_PAID,
                        TICKET_STATUS_ISSUED, now);
                refundMapper.markFailed(idempotentKey,
                        StrUtil.sub(String.valueOf(cause.getMessage()), 0, FAIL_REASON_MAX_LENGTH), now);
            });
        } catch (Exception e) {
            LOG.error("退款失败回滚异常，需人工处理 orderId={}", orderId, e);
        }
    }

    public List<Refund> queryByMember(Long memberId) {
        return refundMapper.selectByMember(memberId);
    }

    public PageResp<RefundResp> QueryList(PageReq req) {
        PageHelper.startPage(req.getPage(), req.getSize());
        List<Refund> list = refundMapper.selectAll();
        PageInfo<Refund> pageInfo = new PageInfo<>(list);
        List<RefundResp> respList = BeanUtil.copyToList(list, RefundResp.class);
        PageResp<RefundResp> pageResp = new PageResp<>();
        pageResp.setTotal(pageInfo.getTotal());
        pageResp.setList(respList);
        return pageResp;
    }
}
