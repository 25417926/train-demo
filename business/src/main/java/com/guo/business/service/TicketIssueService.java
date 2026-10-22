package com.guo.business.service;

import cn.hutool.core.collection.CollUtil;
import com.guo.business.TypeEnum.ConfirmOrderStatusEnum;
import com.guo.business.domain.Confirm_Order;
import com.guo.business.domain.Daily_Train_Seat;
import com.guo.business.domain.Daily_Train_Ticket;
import com.guo.business.domain.InventoryHold;
import com.guo.business.feign.MemberFeign;
import com.guo.business.mapper.Confirm_OrderMapper;
import com.guo.business.mapper.Daily_Train_SeatMapper;
import com.guo.business.mapper.InventoryHoldMapper;
import com.guo.common.req.MemberTicketReq;
import com.guo.common.resp.CommonResp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 出票阶段：支付成功后消费 order_issue 消息，把锁定的座位写成会员车票。
 * 失败只记录 ticket_status=F，座位释放由 I04 的补偿任务统一处理。
 */
@Service
public class TicketIssueService {

    private static final Logger LOG = LoggerFactory.getLogger(TicketIssueService.class);

    private static final String TICKET_STATUS_ISSUED = "S";
    private static final String TICKET_STATUS_FAILED = "F";

    @Resource
    private Confirm_OrderMapper confirm_OrderMapper;
    @Resource
    private InventoryHoldMapper inventoryHoldMapper;
    @Resource
    private Daily_Train_SeatMapper daily_Train_SeatMapper;
    @Resource
    private Daily_Train_TicketService daily_Train_TicketService;
    @Resource
    private MemberFeign memberFeign;

    public void issue(Long orderId) {
        Confirm_Order order = confirm_OrderMapper.selectByPrimaryKey(orderId);
        if (order == null) {
            LOG.error("出票失败，订单不存在：{}", orderId);
            return;
        }
        if (!ConfirmOrderStatusEnum.PAID.getCode().equals(order.getStatus())) {
            LOG.info("订单[{}]当前状态为{}，非已支付，跳过出票", orderId, order.getStatus());
            return;
        }
        String ticketStatus = confirm_OrderMapper.selectTicketStatus(orderId);
        if (TICKET_STATUS_ISSUED.equals(ticketStatus)) {
            LOG.info("订单[{}]已出票，跳过重复出票", orderId);
            return;
        }

        List<InventoryHold> holds = inventoryHoldMapper.selectHeldByOrderId(orderId);
        if (CollUtil.isEmpty(holds)) {
            LOG.error("出票失败，订单[{}]没有有效占座记录", orderId);
            markIssueFailed(orderId, "NO_INVENTORY_HOLD");
            return;
        }

        try {
            // 幂等：会员端已有该订单的车票，说明上次已写成功，仅补状态
            CommonResp<List<Map<String, Object>>> existing = memberFeign.listByOrder(orderId);
            if (existing != null && CollUtil.isNotEmpty(existing.getContent())) {
                LOG.info("订单[{}]会员端已存在车票，直接标记为已出票", orderId);
                markIssued(orderId);
                return;
            }

            Daily_Train_Ticket dailyTrainTicket = daily_Train_TicketService.selectByUnique(
                    order.getDate(), order.getTrainCode(), order.getStart(), order.getEnd());
            if (dailyTrainTicket == null) {
                LOG.error("出票失败，订单[{}]余票记录不存在", orderId);
                markIssueFailed(orderId, "DAILY_TRAIN_TICKET_NOT_EXIST");
                return;
            }

            for (InventoryHold hold : holds) {
                Daily_Train_Seat seat = daily_Train_SeatMapper.selectByPrimaryKey(hold.getSeatId());
                if (seat == null) {
                    LOG.error("出票失败，订单[{}]座位不存在，seatId={}", orderId, hold.getSeatId());
                    markIssueFailed(orderId, "SEAT_NOT_EXIST");
                    return;
                }
                MemberTicketReq memberTicketReq = new MemberTicketReq();
                memberTicketReq.setOrderId(orderId);
                memberTicketReq.setMemberId(order.getMemberId());
                memberTicketReq.setPassengerId(hold.getPassengerId());
                memberTicketReq.setPassengerName(resolvePassengerName(order, hold.getPassengerId()));
                memberTicketReq.setTrainDate(dailyTrainTicket.getDate());
                memberTicketReq.setTrainCode(dailyTrainTicket.getTrainCode());
                memberTicketReq.setCarriageIndex(seat.getCarriageIndex());
                memberTicketReq.setSeatRow(seat.getRow());
                memberTicketReq.setSeatCol(seat.getCol());
                memberTicketReq.setStartStation(dailyTrainTicket.getStart());
                memberTicketReq.setStartTime(dailyTrainTicket.getStartTime());
                memberTicketReq.setEndStation(dailyTrainTicket.getEnd());
                memberTicketReq.setEndTime(dailyTrainTicket.getEndTime());
                memberTicketReq.setSeatType(seat.getSeatType());

                CommonResp<Object> resp = memberFeign.save(memberTicketReq);
                LOG.info("订单[{}]写票结果：{}", orderId, resp);
            }
            markIssued(orderId);
            LOG.info("订单[{}]出票完成，车票数：{}", orderId, holds.size());
        } catch (Exception e) {
            LOG.error("订单[{}]出票异常", orderId, e);
            markIssueFailed(orderId, e.getClass().getSimpleName() + ":" + e.getMessage());
        }
    }

    private void markIssued(Long orderId) {
        confirm_OrderMapper.updateStatusAndTicketStatus(
                orderId, ConfirmOrderStatusEnum.PAID.getCode(), TICKET_STATUS_ISSUED, new Date());
    }

    private void markIssueFailed(Long orderId, String reason) {
        LOG.error("订单[{}]出票失败：{}", orderId, reason);
        confirm_OrderMapper.updateStatusAndTicketStatus(
                orderId, ConfirmOrderStatusEnum.PAID.getCode(), TICKET_STATUS_FAILED, new Date());
    }

    /**
     * 乘客姓名在受理时已随订单 tickets 落库，出票时直接复用
     */
    private String resolvePassengerName(Confirm_Order order, Long passengerId) {
        if (order.getTickets() == null) {
            return null;
        }
        List<Map<String, Object>> tickets = com.alibaba.fastjson.JSON.parseObject(
                order.getTickets(), new com.alibaba.fastjson.TypeReference<List<Map<String, Object>>>() {
                });
        for (Map<String, Object> ticket : tickets) {
            Object id = ticket.get("passengerId");
            if (id != null && String.valueOf(passengerId).equals(String.valueOf(id))) {
                Object name = ticket.get("passengerName");
                return name == null ? null : name.toString();
            }
        }
        return null;
    }
}
