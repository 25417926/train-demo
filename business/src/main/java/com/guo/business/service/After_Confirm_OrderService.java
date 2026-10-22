package com.guo.business.service;

import cn.hutool.core.date.DateUtil;
import com.guo.business.TypeEnum.ConfirmOrderStatusEnum;
import com.guo.business.domain.Confirm_Order;
import com.guo.business.domain.Daily_Train_Seat;
import com.guo.business.domain.Daily_Train_Ticket;
import com.guo.business.domain.InventoryHold;
import com.guo.business.mapper.Confirm_OrderMapper;
import com.guo.business.mapper.InventoryHoldMapper;
import com.guo.business.mapper.cust.CUST_Daily_Train_TicketMapper;
import com.guo.business.req.Confirm_OrderTicketReq;
import com.guo.common.util.SnowUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

/**
 * 锁座阶段：只占座、不写会员票。
 * 支付成功后由 TicketIssueService 异步出票。
 */
@Service
public class After_Confirm_OrderService {

    private static final Logger LOG = LoggerFactory.getLogger(After_Confirm_OrderService.class);

    private static final String HOLD_STATUS_HELD = "H";

    /** 锁座后支付倒计时（分钟） */
    private static final int PAYMENT_TIMEOUT_MINUTES = 15;

    @Resource
    private Daily_Train_SeatService dailyTrainSeatService;
    @Resource
    private CUST_Daily_Train_TicketMapper cust_Daily_Train_TicketMapper;
    @Resource
    private Confirm_OrderMapper confirm_OrderMapper;
    @Resource
    private InventoryHoldMapper inventoryHoldMapper;

    @Transactional
    public void afterDoConfirm(Daily_Train_Ticket dailyTrainTicket,
                               List<Daily_Train_Seat> seatList,
                               List<Confirm_OrderTicketReq> tickets,
                               Confirm_Order confirmOrder) {
        for (int j = 0; j < seatList.size(); j++) {
            Daily_Train_Seat seat = seatList.get(j);

            dailyTrainSeatService.SeatForUpdate(seat);

            int[] influence = calcInfluenceRange(
                    seat.getSell(),
                    dailyTrainTicket.getStartIndex(),
                    dailyTrainTicket.getEndIndex()
            );
            cust_Daily_Train_TicketMapper.updateCountBySell(
                    seat.getDate(),
                    seat.getTrainCode(),
                    seat.getSeatType(),
                    influence[0],
                    influence[1],
                    influence[2],
                    influence[3]
            );
        }

        Date now = new Date();
        String holdToken = "HOLD-" + confirmOrder.getId();
        for (int j = 0; j < seatList.size(); j++) {
            Daily_Train_Seat seat = seatList.get(j);
            Confirm_OrderTicketReq ticket = tickets.get(j);

            InventoryHold hold = new InventoryHold();
            hold.setId(SnowUtil.getSnowflakeNextId());
            hold.setHoldToken(holdToken);
            hold.setRequestId(String.valueOf(confirmOrder.getId()));
            hold.setOrderId(confirmOrder.getId());
            hold.setMemberId(confirmOrder.getMemberId());
            hold.setPassengerId(ticket.getPassengerId());
            hold.setDate(seat.getDate());
            hold.setTrainCode(seat.getTrainCode());
            hold.setSeatId(seat.getId());
            hold.setSeatType(seat.getSeatType());
            hold.setStartIndex(dailyTrainTicket.getStartIndex());
            hold.setEndIndex(dailyTrainTicket.getEndIndex());
            hold.setStatus(HOLD_STATUS_HELD);
            hold.setCreateTime(now);
            inventoryHoldMapper.insert(hold);
        }

        // 锁座成功：订单进入待支付（S），车票尚未出（N），并写入支付截止时间
        Date expireAt = DateUtil.offsetMinute(now, PAYMENT_TIMEOUT_MINUTES);
        confirm_OrderMapper.updateLockResult(
                confirmOrder.getId(),
                ConfirmOrderStatusEnum.SUCCESS.getCode(),
                "N",
                expireAt,
                now);
        LOG.info("订单[{}]锁座完成，座位数：{}，支付截止：{}", confirmOrder.getId(), seatList.size(), expireAt);
    }

    private int[] calcInfluenceRange(String sell, Integer startIndex, Integer endIndex) {
        char[] sellChars = sell.toCharArray();
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
}
