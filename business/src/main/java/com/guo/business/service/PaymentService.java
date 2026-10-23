package com.guo.business.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSON;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.guo.business.TypeEnum.ConfirmOrderStatusEnum;
import com.guo.business.TypeEnum.PaymentStatusEnum;
import com.guo.business.TypeEnum.RocketMqEnum;
import com.guo.business.domain.Confirm_Order;
import com.guo.business.domain.Payment;
import com.guo.business.mapper.Confirm_OrderMapper;
import com.guo.business.mapper.PaymentMapper;
import com.guo.business.req.OrderIssueDto;
import com.guo.business.req.PaymentReq;
import com.guo.business.resp.PaymentResp;
import com.guo.common.exception.ExceptionConsole;
import com.guo.common.exception.ExceptionEnum;
import com.guo.common.req.PageReq;
import com.guo.common.resp.PageResp;
import com.guo.common.util.SnowUtil;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

/**
 * 支付服务
 *
 * 注：本项目为演示项目，未对接真实第三方支付通道。
 *     pay 接口直接将支付记录置为已支付，并把订单状态推进到 PAID。
 *
 * @Description
 * @Author: 派
 * @Date: 2026/06/04
 */
@Service
public class PaymentService {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentService.class);

    @Resource
    private PaymentMapper paymentMapper;
    @Resource
    private Confirm_OrderMapper confirm_OrderMapper;
    @Resource
    private RocketMQTemplate rocketMQTemplate;
    @Resource
    private OrderCloseQueue orderCloseQueue;

    /**
     * 发起支付（演示场景：直接置为支付成功）
     */
    @Transactional
    public PaymentResp pay(PaymentReq req, Long memberId) {
        LOG.info("发起支付：{}", req);
        Confirm_Order order = confirm_OrderMapper.selectByPrimaryKey(req.getOrderId());
        
        if (ObjectUtil.isNull(order)) {
            throw new ExceptionConsole(ExceptionEnum.ORDER_NOT_EXIST);
        }
        if (!order.getMemberId().equals(memberId)) {
            throw new ExceptionConsole(ExceptionEnum.ORDER_MEMBER_ERROR);
        }
        // 锁座成功（S）与候补补位（H）两类待支付订单均可支付；出票在支付成功后异步完成
        String fromStatus = order.getStatus();
        if (!ConfirmOrderStatusEnum.SUCCESS.getCode().equals(fromStatus)
                && !ConfirmOrderStatusEnum.WAITLIST_PAY.getCode().equals(fromStatus)) {
            if (ConfirmOrderStatusEnum.PAID.getCode().equals(fromStatus)) {
                throw new ExceptionConsole(ExceptionEnum.PAYMENT_ALREADY_DONE);
            }
            throw new ExceptionConsole(ExceptionEnum.ORDER_STATUS_ERROR);
        }

        DateTime now = DateTime.now();
        // 同一订单只保留一条支付记录：失败重试成功后复用原记录并置为 S
        Payment payment = paymentMapper.selectByOrderId(order.getId());
        if (ObjectUtil.isNull(payment)) {
            payment = new Payment();
            payment.setId(SnowUtil.getSnowflakeNextId());
            payment.setOrderId(req.getOrderId());
            payment.setMemberId(memberId);
            payment.setAmount(req.getAmount());
            payment.setPayChannel(req.getPayChannel());
            payment.setPayStatus(PaymentStatusEnum.SUCCESS.getCode());
            payment.setPayTime(now);
            payment.setTradeNo("DEMO-" + payment.getId());
            payment.setCreateTime(now);
            payment.setUpdateTime(now);
            paymentMapper.insert(payment);
        } else {
            payment.setAmount(req.getAmount());
            payment.setPayChannel(req.getPayChannel());
            payment.setPayStatus(PaymentStatusEnum.SUCCESS.getCode());
            payment.setPayTime(now);
            payment.setTradeNo("DEMO-" + payment.getId());
            payment.setFailReason("");
            payment.setUpdateTime(now);
            paymentMapper.updateByPrimaryKeySelective(payment);
        }

        // 推进订单状态到 PAID：CAS 防止并发下订单已被超时关闭后仍支付成功
        if (confirm_OrderMapper.updateStatusCas(order.getId(),
                fromStatus, ConfirmOrderStatusEnum.PAID.getCode(), now) != 1) {
            LOG.warn("订单【{}】支付时状态已变更（可能已超时关闭），支付回滚", order.getId());
            throw new ExceptionConsole(ExceptionEnum.ORDER_STATUS_ERROR);
        }
        LOG.info("订单【{}】支付成功，流水号：{}", order.getId(), payment.getTradeNo());

        // 支付成功才出票：事务提交后投递 order_issue，消费者写会员车票并回写 ticket_status
        OrderIssueDto issueDto = new OrderIssueDto();
        issueDto.setOrderId(order.getId());
        issueDto.setLogId(MDC.get("LOG_ID"));
        Runnable sendIssue = () -> sendIssueMessage(issueDto);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    // I12：支付成功后移出超时关闭队列，避免被定时任务误关
                    orderCloseQueue.cancel(order.getId());
                    sendIssue.run();
                }
            });
        } else {
            sendIssue.run();
        }

        return BeanUtil.copyProperties(payment, PaymentResp.class);
    }

    private void sendIssueMessage(OrderIssueDto issueDto) {
        String body = JSON.toJSONString(issueDto);
        try {
            rocketMQTemplate.convertAndSend(RocketMqEnum.ORDER_ISSUE.getCode(), body);
            LOG.info("支付成功，已投递出票消息：{}", body);
        } catch (Exception e) {
            // 出票消息投递失败由 I04 的补偿任务重试，先记录
            LOG.error("出票消息投递失败：{}", body, e);
        }
    }

    /**
     * 根据订单号查询支付详情
     */
    public PaymentResp queryByOrderId(Long orderId) {
        Payment payment = paymentMapper.selectByOrderId(orderId);
        if (ObjectUtil.isNull(payment)) {
            throw new ExceptionConsole(ExceptionEnum.PAYMENT_NOT_EXIST);
        }
        return BeanUtil.copyProperties(payment, PaymentResp.class);
    }

    /**
     * 标记为已退款（由 RefundService 内部调用）
     */
    public void markRefunded(Long paymentId) {
        Payment forUpdate = new Payment();
        forUpdate.setId(paymentId);
        forUpdate.setPayStatus(PaymentStatusEnum.REFUNDED.getCode());
        forUpdate.setUpdateTime(new Date());
        paymentMapper.updateByPrimaryKeySelective(forUpdate);
    }

    /**
     * 后台分页查询所有支付记录
     */
    public PageResp<PaymentResp> QueryList(PageReq req) {
        PageHelper.startPage(req.getPage(), req.getSize());
        List<Payment> list = paymentMapper.selectAll();
        PageInfo<Payment> pageInfo = new PageInfo<>(list);
        List<PaymentResp> respList = BeanUtil.copyToList(list, PaymentResp.class);
        PageResp<PaymentResp> pageResp = new PageResp<>();
        pageResp.setTotal(pageInfo.getTotal());
        pageResp.setList(respList);
        return pageResp;
    }
}
