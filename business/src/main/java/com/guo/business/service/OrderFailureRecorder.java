package com.guo.business.service;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.guo.business.TypeEnum.ChangeStatusEnum;
import com.guo.business.TypeEnum.PaymentStatusEnum;
import com.guo.business.TypeEnum.RefundStatusEnum;
import com.guo.business.domain.ChangeRecord;
import com.guo.business.domain.Payment;
import com.guo.business.domain.Refund;
import com.guo.business.mapper.ChangeRecordMapper;
import com.guo.business.mapper.PaymentMapper;
import com.guo.business.mapper.RefundMapper;
import com.guo.common.exception.ExceptionConsole;
import com.guo.common.exception.ExceptionEnum;
import com.guo.common.util.SnowUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 失败流水记录器。
 *
 * 业务事务失败回滚后调用，用独立事务把失败结果落库，
 * 避免"失败无记录"或被原事务回滚。
 */
@Service
public class OrderFailureRecorder {

    private static final Logger LOG = LoggerFactory.getLogger(OrderFailureRecorder.class);
    private static final BigDecimal DEMO_CHANGE_FEE = new BigDecimal("5.00");
    private static final int REASON_MAX_LENGTH = 500;

    @Resource
    private PaymentMapper paymentMapper;
    @Resource
    private RefundMapper refundMapper;
    @Resource
    private ChangeRecordMapper changeRecordMapper;

    /**
     * 支付失败：同一订单保留一条支付记录，已有成功/退款记录时不再写失败
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordPaymentFail(Long orderId, Long memberId, BigDecimal amount,
                                  String payChannel, String reason) {
        try {
            Date now = new Date();
            Payment existing = paymentMapper.selectByOrderId(orderId);
            if (ObjectUtil.isNotNull(existing)
                    && !PaymentStatusEnum.FAIL.getCode().equals(existing.getPayStatus())) {
                return;
            }
            if (ObjectUtil.isNull(existing)) {
                Payment payment = new Payment();
                payment.setId(SnowUtil.getSnowflakeNextId());
                payment.setOrderId(orderId);
                payment.setMemberId(memberId);
                payment.setAmount(amount == null ? BigDecimal.ZERO : amount);
                payment.setPayChannel(StrUtil.blankToDefault(payChannel, "A"));
                payment.setPayStatus(PaymentStatusEnum.FAIL.getCode());
                payment.setFailReason(truncate(reason));
                payment.setCreateTime(now);
                payment.setUpdateTime(now);
                paymentMapper.insert(payment);
            } else {
                existing.setAmount(amount == null ? BigDecimal.ZERO : amount);
                existing.setPayChannel(StrUtil.blankToDefault(payChannel, "A"));
                existing.setFailReason(truncate(reason));
                existing.setUpdateTime(now);
                paymentMapper.updateByPrimaryKeySelective(existing);
            }
            LOG.info("记录支付失败流水，orderId={}，reason={}", orderId, reason);
        } catch (Exception e) {
            LOG.error("记录支付失败流水异常，orderId={}", orderId, e);
        }
    }

    /**
     * 退款失败：独立插入一条 F 流水，保留失败原因
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRefundFail(Long orderId, Long memberId, String reason) {
        try {
            Payment payment = paymentMapper.selectByOrderId(orderId);
            Date now = new Date();
            Refund refund = new Refund();
            refund.setId(SnowUtil.getSnowflakeNextId());
            refund.setOrderId(orderId);
            refund.setPaymentId(ObjectUtil.isNull(payment) ? 0L : payment.getId());
            refund.setMemberId(memberId);
            refund.setAmount(ObjectUtil.isNull(payment) ? BigDecimal.ZERO : payment.getAmount());
            refund.setReason("退款失败");
            refund.setFailReason(truncate(reason));
            refund.setRefundStatus(RefundStatusEnum.FAIL.getCode());
            refund.setCreateTime(now);
            refund.setUpdateTime(now);
            refundMapper.insert(refund);
            LOG.info("记录退款失败流水，orderId={}，reason={}", orderId, reason);
        } catch (Exception e) {
            LOG.error("记录退款失败流水异常，orderId={}", orderId, e);
        }
    }

    /**
     * 改签失败：独立插入一条 F 记录，原事务回滚不会带走它
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordChangeFail(Long originalOrderId, Long memberId, String reason) {
        try {
            Date now = new Date();
            ChangeRecord record = new ChangeRecord();
            record.setId(SnowUtil.getSnowflakeNextId());
            record.setOriginalOrderId(originalOrderId);
            record.setMemberId(memberId);
            record.setChangeType("C");
            record.setChangeStatus(ChangeStatusEnum.FAIL.getCode());
            record.setFee(DEMO_CHANGE_FEE);
            record.setFailReason(truncate(reason));
            record.setCreateTime(now);
            record.setUpdateTime(now);
            changeRecordMapper.insert(record);
            LOG.info("记录改签失败流水，originalOrderId={}，reason={}", originalOrderId, reason);
        } catch (Exception e) {
            LOG.error("记录改签失败流水异常，originalOrderId={}", originalOrderId, e);
        }
    }

    /**
     * 业务异常（ExceptionConsole）不携带 message，这里统一取可读的失败原因
     */
    public static String reasonOf(Throwable e) {
        if (e instanceof ExceptionConsole) {
            ExceptionEnum exceptionEnum = ((ExceptionConsole) e).getExceptionEnum();
            if (exceptionEnum != null && StrUtil.isNotBlank(exceptionEnum.getMessage())) {
                return exceptionEnum.getMessage();
            }
        }
        return StrUtil.blankToDefault(e.getMessage(), e.getClass().getSimpleName());
    }

    private String truncate(String reason) {
        if (StrUtil.isBlank(reason)) {
            return "";
        }
        return reason.length() <= REASON_MAX_LENGTH ? reason : reason.substring(0, REASON_MAX_LENGTH);
    }
}
