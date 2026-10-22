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
import com.guo.common.req.TicketIssueReq;
import com.guo.common.resp.CommonResp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
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

        try {
            issueTickets(order);
            markIssued(orderId);
        } catch (Exception e) {
            LOG.error("订单[{}]出票异常", orderId, e);
            markIssueFailed(orderId, e.getClass().getSimpleName() + ":" + e.getMessage());
        }
    }

    /**
     * I10：按订单组装车票并调用 member 批量出票接口。
     *
     * <p>member 侧按 (order_id, passenger_id) 唯一键幂等写入，因此事件重投不会重复发票。
     * 失败直接抛出，由 TICKET_CREATE 消费端重投。</p>
     */
    public void issueTickets(Confirm_Order order) {
        List<MemberTicketReq> requests = buildTicketRequests(order);

        TicketIssueReq req = new TicketIssueReq();
        req.setOrderId(order.getId());
        req.setTickets(requests);
        CommonResp<Integer> resp = memberFeign.issueTickets(req);
        if (resp == null || !resp.getSuccess()) {
            throw new IllegalStateException("member 出票失败：" + (resp == null ? "null" : resp.getMessage()));
        }
        LOG.info("订单[{}]出票完成，车票数：{}，新增：{}", order.getId(), requests.size(), resp.getContent());
    }

    /**
     * 按订单组装车票明细（I10 事件携带 / 支付后出票共用）
     */
    public List<MemberTicketReq> buildTicketRequests(Confirm_Order order) {
        List<InventoryHold> holds = inventoryHoldMapper.selectHeldByOrderId(order.getId());
        if (CollUtil.isEmpty(holds)) {
            throw new IllegalStateException("NO_INVENTORY_HOLD");
        }

        Daily_Train_Ticket dailyTrainTicket = daily_Train_TicketService.selectByUnique(
                order.getDate(), order.getTrainCode(), order.getStart(), order.getEnd());
        if (dailyTrainTicket == null) {
            throw new IllegalStateException("DAILY_TRAIN_TICKET_NOT_EXIST");
        }

        List<MemberTicketReq> requests = new ArrayList<>(holds.size());
        for (InventoryHold hold : holds) {
            Daily_Train_Seat seat = daily_Train_SeatMapper.selectByPrimaryKey(hold.getSeatId());
            if (seat == null) {
                throw new IllegalStateException("SEAT_NOT_EXIST:" + hold.getSeatId());
            }
            MemberTicketReq req = new MemberTicketReq();
            req.setOrderId(order.getId());
            req.setMemberId(order.getMemberId());
            req.setPassengerId(hold.getPassengerId());
            req.setPassengerName(resolvePassengerName(order, hold.getPassengerId()));
            req.setTrainDate(dailyTrainTicket.getDate());
            req.setTrainCode(dailyTrainTicket.getTrainCode());
            req.setCarriageIndex(seat.getCarriageIndex());
            req.setSeatRow(seat.getRow());
            req.setSeatCol(seat.getCol());
            req.setStartStation(dailyTrainTicket.getStart());
            req.setStartTime(dailyTrainTicket.getStartTime());
            req.setEndStation(dailyTrainTicket.getEnd());
            req.setEndTime(dailyTrainTicket.getEndTime());
            req.setSeatType(seat.getSeatType());
            requests.add(req);
        }
        return requests;
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
