package com.guo.business.service;

import com.guo.business.TypeEnum.ConfirmOrderStatusEnum;
import com.guo.business.domain.Confirm_Order;
import com.guo.business.domain.Daily_Train_Seat;
import com.guo.business.domain.Daily_Train_Ticket;
import com.guo.business.feign.MemberFeign;
import com.guo.business.mapper.Confirm_OrderMapper;
import com.guo.business.mapper.cust.CUST_Daily_Train_TicketMapper;
import com.guo.business.req.Confirm_OrderTicketReq;
import com.guo.common.req.MemberTicketReq;
import com.guo.common.resp.CommonResp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

@Service
public class After_Confirm_OrderService {

    private static final Logger LOG = LoggerFactory.getLogger(After_Confirm_OrderService.class);

    @Resource
    private Daily_Train_SeatService dailyTrainSeatService;
    @Resource
    private CUST_Daily_Train_TicketMapper cust_Daily_Train_TicketMapper;
    @Resource
    private MemberFeign memberFeign;
    @Resource
    private Confirm_OrderMapper confirm_OrderMapper;

    @Transactional
    public void afterDoConfirm(Daily_Train_Ticket dailyTrainTicket,
                               List<Daily_Train_Seat> seatList,
                               List<Confirm_OrderTicketReq> tickets,
                               Confirm_Order confirmOrder) {
        for (int j = 0; j < seatList.size(); j++) {
            Daily_Train_Seat seat = seatList.get(j);
            Confirm_OrderTicketReq ticket = tickets.get(j);

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

            MemberTicketReq memberTicketReq = new MemberTicketReq();
            memberTicketReq.setMemberId(confirmOrder.getMemberId());
            memberTicketReq.setPassengerId(ticket.getPassengerId());
            memberTicketReq.setPassengerName(ticket.getPassengerName());
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
            CommonResp<Object> memberTicketResp = memberFeign.save(memberTicketReq);
            LOG.info("member ticket save result: {}", memberTicketResp);

            Confirm_Order confirmOrderForUpdate = new Confirm_Order();
            confirmOrderForUpdate.setId(confirmOrder.getId());
            confirmOrderForUpdate.setUpdateTime(new Date());
            confirmOrderForUpdate.setStatus(ConfirmOrderStatusEnum.SUCCESS.getCode());
            confirm_OrderMapper.updateByPrimaryKeySelective(confirmOrderForUpdate);
        }
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
