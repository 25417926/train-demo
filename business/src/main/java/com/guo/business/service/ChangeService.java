package com.guo.business.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSON;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.guo.business.TypeEnum.ChangeStatusEnum;
import com.guo.business.TypeEnum.ConfirmOrderStatusEnum;
import com.guo.business.domain.ChangeRecord;
import com.guo.business.domain.Confirm_Order;
import com.guo.business.mapper.ChangeRecordMapper;
import com.guo.business.mapper.Confirm_OrderMapper;
import com.guo.business.req.ChangeReq;
import com.guo.business.req.Confirm_OrderDoReq;
import com.guo.business.req.Confirm_OrderTicketReq;
import com.guo.business.req.RefundReq;
import com.guo.business.resp.ChangeRecordResp;
import com.guo.common.exception.ExceptionConsole;
import com.guo.common.exception.ExceptionEnum;
import com.guo.common.req.PageReq;
import com.guo.common.resp.PageResp;
import com.guo.common.util.SnowUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.List;

/**
 * 退改签服务（厚实现）：
 *   改签 = 退原票 + 重新下单
 *   流程：
 *     1. 校验原订单状态（已支付才允许改签）
 *     2. 复用 RefundService.refund 完成退原票（回滚 sell/余票/删车票）
 *     3. 复用 Before_Confirm_OrderService.BeforeDoConfirm 完成新订单的令牌校验+落库+发 MQ
 *     4. 落 change_record 记录，关联 originalOrderId / newOrderId
 *
 * 注：改签后的实际出票仍由 ConfirmOrderConsumer 异步消费，落地为 SUCCESS 状态。
 *     扣款/差价不在演示范围内，手续费按演示金额 5 元写入。
 *
 * @Description
 * @Author: 派
 * @Date: 2026/06/04
 */
@Service
public class ChangeService {

    private static final Logger LOG = LoggerFactory.getLogger(ChangeService.class);

    /** 演示用固定改签手续费 */
    private static final BigDecimal DEMO_CHANGE_FEE = new BigDecimal("5.00");

    @Resource
    private ChangeRecordMapper changeRecordMapper;
    @Resource
    private Confirm_OrderMapper confirm_OrderMapper;
    @Resource
    private RefundService refundService;
    @Resource
    private Before_Confirm_OrderService before_Confirm_OrderService;

    @Transactional
    public ChangeRecordResp change(ChangeReq req, Long memberId) {
        LOG.info("发起改签：{}", req);
        Confirm_Order original = confirm_OrderMapper.selectByPrimaryKey(req.getOriginalOrderId());
        if (ObjectUtil.isNull(original)) {
            throw new ExceptionConsole(ExceptionEnum.ORDER_NOT_EXIST);
        }
        if (ConfirmOrderStatusEnum.CHANGED.getCode().equals(original.getStatus())) {
            throw new ExceptionConsole(ExceptionEnum.CHANGE_ALREADY_DONE);
        }
        if (!ConfirmOrderStatusEnum.PAID.getCode().equals(original.getStatus())) {
            throw new ExceptionConsole(ExceptionEnum.ORDER_STATUS_ERROR);
        }

        DateTime now = DateTime.now();
        ChangeRecord record = new ChangeRecord();
        record.setId(SnowUtil.getSnowflakeNextId());
        record.setOriginalOrderId(original.getId());
        record.setMemberId(memberId);
        record.setChangeType("C");
        record.setChangeStatus(ChangeStatusEnum.INIT.getCode());
        record.setFee(DEMO_CHANGE_FEE);
        record.setCreateTime(now);
        record.setUpdateTime(now);
        changeRecordMapper.insert(record);

        try {
            // 1) 退原票（沿用 RefundService 的厚实现）
            RefundReq refundReq = new RefundReq();
            refundReq.setOrderId(original.getId());
            refundReq.setReason("改签自动退票");
            refundService.refund(refundReq, memberId);

            // 2) 把原订单状态由 REFUNDED 修正为 CHANGED（语义更准确）
            Confirm_Order forUpdate = new Confirm_Order();
            forUpdate.setId(original.getId());
            forUpdate.setStatus(ConfirmOrderStatusEnum.CHANGED.getCode());
            forUpdate.setUpdateTime(new DateTime());
            confirm_OrderMapper.updateByPrimaryKeySelective(forUpdate);

            // 3) 走原下单流程提交新订单（令牌+MQ 异步出票）
            Confirm_OrderDoReq newOrder = req.getNewOrder();
            fillTicketsFromOriginalIfAbsent(newOrder, original);
            newOrder.setMemberId(memberId);
            Long newOrderId = before_Confirm_OrderService.BeforeDoConfirm(newOrder);

            // 4) 标记 change_record 成功
            ChangeRecord recordUpdate = new ChangeRecord();
            recordUpdate.setId(record.getId());
            recordUpdate.setNewOrderId(newOrderId);
            recordUpdate.setChangeStatus(ChangeStatusEnum.SUCCESS.getCode());
            recordUpdate.setUpdateTime(new DateTime());
            changeRecordMapper.updateByPrimaryKeySelective(recordUpdate);
            record.setNewOrderId(newOrderId);
            record.setChangeStatus(ChangeStatusEnum.SUCCESS.getCode());
            LOG.info("改签成功，原订单【{}】，新订单【{}】", original.getId(), newOrderId);
        } catch (Exception e) {
            // 失败记录由 OrderFailureRecorder 用独立事务落库，避免随本事务回滚
            LOG.error("改签失败：{}", e.getMessage());
            throw e;
        }
        return BeanUtil.copyProperties(record, ChangeRecordResp.class);
    }

    private void fillTicketsFromOriginalIfAbsent(Confirm_OrderDoReq newOrder, Confirm_Order original) {
        if (CollUtil.isNotEmpty(newOrder.getTickets())) {
            return;
        }
        List<Confirm_OrderTicketReq> originalTickets = JSON.parseArray(
                original.getTickets(), Confirm_OrderTicketReq.class);
        if (CollUtil.isEmpty(originalTickets)) {
            LOG.error("original order {} has no tickets to reuse for change", original.getId());
            throw new ExceptionConsole(ExceptionEnum.PARAM_NOT_VALID);
        }
        newOrder.setTickets(originalTickets);
    }

    /**
     * 后台分页查询所有退改签记录
     */
    public PageResp<ChangeRecordResp> QueryList(PageReq req) {
        PageHelper.startPage(req.getPage(), req.getSize());
        List<ChangeRecord> list = changeRecordMapper.selectAll();
        PageInfo<ChangeRecord> pageInfo = new PageInfo<>(list);
        List<ChangeRecordResp> respList = BeanUtil.copyToList(list, ChangeRecordResp.class);
        PageResp<ChangeRecordResp> pageResp = new PageResp<>();
        pageResp.setTotal(pageInfo.getTotal());
        pageResp.setList(respList);
        return pageResp;
    }
}
