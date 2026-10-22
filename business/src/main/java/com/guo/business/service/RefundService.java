package com.guo.business.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.guo.business.TypeEnum.ConfirmOrderStatusEnum;
import com.guo.business.TypeEnum.RefundStatusEnum;
import com.guo.business.domain.Confirm_Order;
import com.guo.business.domain.Daily_Train_Seat;
import com.guo.business.domain.Daily_Train_Ticket;
import com.guo.business.domain.Payment;
import com.guo.business.domain.Refund;
import com.guo.business.feign.MemberFeign;
import com.guo.business.mapper.Confirm_OrderMapper;
import com.guo.business.mapper.PaymentMapper;
import com.guo.business.mapper.RefundMapper;
import com.guo.business.mapper.cust.CUST_Daily_Train_TicketMapper;
import com.guo.business.req.RefundReq;
import com.guo.business.resp.RefundResp;
import com.guo.common.exception.ExceptionConsole;
import com.guo.common.exception.ExceptionEnum;
import com.guo.common.req.PageReq;
import com.guo.common.resp.CommonResp;
import com.guo.common.resp.PageResp;
import com.guo.common.util.SnowUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class RefundService {

    private static final Logger LOG = LoggerFactory.getLogger(RefundService.class);

    @Resource
    private RefundMapper refundMapper;
    @Resource
    private PaymentMapper paymentMapper;
    @Resource
    private Confirm_OrderMapper confirm_OrderMapper;
    @Resource
    private Daily_Train_TicketService daily_Train_TicketService;
    @Resource
    private Daily_Train_SeatService daily_Train_SeatService;
    @Resource
    private CUST_Daily_Train_TicketMapper cust_Daily_Train_TicketMapper;
    @Resource
    private PaymentService paymentService;
    @Resource
    private MemberFeign memberFeign;

    @Transactional
    public RefundResp refund(RefundReq req, Long memberId) {
        LOG.info("发起退款：{}", req);

        Confirm_Order order = confirm_OrderMapper.selectByPrimaryKey(req.getOrderId());
        if (ObjectUtil.isNull(order)) {
            throw new ExceptionConsole(ExceptionEnum.ORDER_NOT_EXIST);
        }
        if (!order.getMemberId().equals(memberId)) {
            throw new ExceptionConsole(ExceptionEnum.ORDER_MEMBER_ERROR);
        }
        if (ConfirmOrderStatusEnum.REFUNDED.getCode().equals(order.getStatus())) {
            throw new ExceptionConsole(ExceptionEnum.REFUND_ALREADY_DONE);
        }
        if (!ConfirmOrderStatusEnum.PAID.getCode().equals(order.getStatus())) {
            throw new ExceptionConsole(ExceptionEnum.ORDER_STATUS_ERROR);
        }
        // 支付成功后才异步出票，未出票时不能退款
        if (!"S".equals(confirm_OrderMapper.selectTicketStatus(order.getId()))) {
            throw new ExceptionConsole(ExceptionEnum.TICKET_ISSUING);
        }

        Payment payment = paymentMapper.selectByOrderId(order.getId());
        if (ObjectUtil.isNull(payment)) {
            throw new ExceptionConsole(ExceptionEnum.PAYMENT_NOT_EXIST);
        }

        Daily_Train_Ticket dailyTicket = daily_Train_TicketService.selectByUnique(
                order.getDate(), order.getTrainCode(), order.getStart(), order.getEnd());
        if (ObjectUtil.isNull(dailyTicket)) {
            throw new ExceptionConsole(ExceptionEnum.TICKET_NOT_EXIST);
        }

        CommonResp<List<Map<String, Object>>> resp = memberFeign.listByOrder(order.getId());
        List<Map<String, Object>> ticketList = ObjectUtil.isNotNull(resp) ? resp.getContent() : null;
        if (CollUtil.isEmpty(ticketList)) {
            LOG.error("refund tickets not found, orderId={}", order.getId());
            throw new ExceptionConsole(ExceptionEnum.FAILURE_TO_SAVE_TICKET);
        }

        List<RefundTicketContext> refundTickets = buildRefundTickets(order, ticketList);
        for (RefundTicketContext refundTicket : refundTickets) {
            rollbackSell(refundTicket.seat, dailyTicket.getStartIndex(), dailyTicket.getEndIndex());
            daily_Train_SeatService.SeatForUpdate(refundTicket.seat);

            int[] influence = calcInfluence(
                    refundTicket.seat.getSell(),
                    dailyTicket.getStartIndex(),
                    dailyTicket.getEndIndex()
            );
            cust_Daily_Train_TicketMapper.increaseCountBySell(
                    order.getDate(),
                    order.getTrainCode(),
                    refundTicket.seat.getSeatType(),
                    influence[0],
                    influence[1],
                    influence[2],
                    influence[3]
            );
        }

        // I10：按订单精确删除本单车票，避免逐个 id 删除时误删或漏删
        memberFeign.deleteTicketsByOrder(order.getId());

        paymentService.markRefunded(payment.getId());

        DateTime now = DateTime.now();
        // 退票成功后车票已删除，订单标记为已退款，ticket_status 同步回到未出票
        confirm_OrderMapper.updateStatusAndTicketStatus(
                order.getId(), ConfirmOrderStatusEnum.REFUNDED.getCode(), "N", now);

        Refund refund = new Refund();
        refund.setId(SnowUtil.getSnowflakeNextId());
        refund.setOrderId(order.getId());
        refund.setPaymentId(payment.getId());
        refund.setMemberId(memberId);
        refund.setAmount(payment.getAmount());
        refund.setReason(req.getReason());
        refund.setRefundStatus(RefundStatusEnum.SUCCESS.getCode());
        refund.setRefundTime(now);
        refund.setCreateTime(now);
        refund.setUpdateTime(now);
        refundMapper.insert(refund);
        LOG.info("订单[{}]退款完成", order.getId());

        return BeanUtil.copyProperties(refund, RefundResp.class);
    }

    private List<RefundTicketContext> buildRefundTickets(Confirm_Order order, List<Map<String, Object>> ticketList) {
        List<RefundTicketContext> refundTickets = new ArrayList<>(ticketList.size());
        for (Map<String, Object> ticket : ticketList) {
            Integer carriageIndex = readRequiredInteger(ticket, "carriageIndex");
            String seatRow = readRequiredString(ticket, "seatRow");
            String seatCol = readRequiredString(ticket, "seatCol");

            Daily_Train_Seat seat = daily_Train_SeatService.selectByPosition(
                    order.getDate(), order.getTrainCode(), carriageIndex, seatRow, seatCol);
            if (ObjectUtil.isNull(seat)) {
                LOG.error(
                        "refund seat not found, orderId={}, carriageIndex={}, seatRow={}, seatCol={}",
                        order.getId(), carriageIndex, seatRow, seatCol
                );
                throw new ExceptionConsole(ExceptionEnum.FAILURE_TO_SAVE_TICKET);
            }

            refundTickets.add(new RefundTicketContext(seat));
        }
        return refundTickets;
    }

    private void rollbackSell(Daily_Train_Seat seat, Integer startIndex, Integer endIndex) {
        String sell = seat.getSell();
        int sellStartIndex = startIndex - 1;
        int sellEndIndex = endIndex - 1;

        StringBuilder maskBuilder = new StringBuilder();
        for (int i = 0; i < sell.length(); i++) {
            maskBuilder.append((i >= sellStartIndex && i < sellEndIndex) ? '1' : '0');
        }
        String mask = maskBuilder.toString();
        long newSell = NumberUtil.binaryToInt(sell) & (~NumberUtil.binaryToInt(mask));
        String newSellStr = NumberUtil.getBinaryStr(newSell);
        newSellStr = StrUtil.fillBefore(newSellStr, '0', sell.length());
        LOG.info("回滚座位 {} 的 sell：{} -> {}", seat.getCarriageSeatIndex(), sell, newSellStr);
        seat.setSell(newSellStr);
    }

    private int[] calcInfluence(String newSell, Integer startIndex, Integer endIndex) {
        char[] sellChars = newSell.toCharArray();
        int sellStartIndex = startIndex - 1;
        int sellEndIndex = endIndex - 1;

        int minStartIndex = 1;
        for (int i = sellStartIndex - 1; i >= 0; i--) {
            if (sellChars[i] == '1') {
                minStartIndex = i + 2;
                break;
            }
        }

        int maxStartIndex = endIndex - 1;
        int minEndIndex = startIndex + 1;
        int maxEndIndex = sellChars.length + 1;
        for (int i = sellEndIndex; i < sellChars.length; i++) {
            if (sellChars[i] == '1') {
                maxEndIndex = i + 1;
                break;
            }
        }
        return new int[]{minStartIndex, maxStartIndex, minEndIndex, maxEndIndex};
    }

    private Integer readRequiredInteger(Map<String, Object> ticket, String fieldName) {
        Object value = ticket.get(fieldName);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String && StrUtil.isNotBlank((String) value)) {
            return Integer.parseInt((String) value);
        }
        LOG.error("ticket field {} is invalid, value={}", fieldName, value);
        throw new ExceptionConsole(ExceptionEnum.FAILURE_TO_SAVE_TICKET);
    }

    private String readRequiredString(Map<String, Object> ticket, String fieldName) {
        Object value = ticket.get(fieldName);
        if (ObjectUtil.isNull(value)) {
            LOG.error("ticket field {} is null", fieldName);
            throw new ExceptionConsole(ExceptionEnum.FAILURE_TO_SAVE_TICKET);
        }
        String text = value.toString();
        if (StrUtil.isBlank(text)) {
            LOG.error("ticket field {} is blank", fieldName);
            throw new ExceptionConsole(ExceptionEnum.FAILURE_TO_SAVE_TICKET);
        }
        return text;
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

    private static class RefundTicketContext {
        private final Daily_Train_Seat seat;

        private RefundTicketContext(Daily_Train_Seat seat) {
            this.seat = seat;
        }
    }
}
