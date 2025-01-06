package com.guo.business.service;


import com.guo.business.domain.Daily_Train_Seat;
import com.guo.business.domain.Daily_Train_Ticket;
import com.guo.business.mapper.cust.CUST_Daily_Train_TicketMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

/**
 * 确认订单服务类
 */
@Service
public class After_Confirm_OrderService {

    private static final Logger LOG = LoggerFactory.getLogger(After_Confirm_OrderService.class);

    @Resource
    private Daily_Train_SeatService Daily_Train_SeatService;
    @Resource
    private CUST_Daily_Train_TicketMapper cust_Daily_Train_TicketMapper;

    /**
     * 选中座位后事务处理：
     *  座位表修改售卖情况sell；
     *  余票详情表修改余票；
     *  为会员增加购票记录
     *  更新确认订单为成功
     */

    @Transactional
    public void afterDoConfirm(Daily_Train_Ticket daily_Train_Ticket,List<Daily_Train_Seat> seatList) {
        for(Daily_Train_Seat seat:seatList){
            Daily_Train_SeatService.SeatForUpdate(seat);
            //        假设10个站，本次购买的为4-7站
//
//                - 原售： 001000001
//                - 购买： 000011100
//                - 新售： 001011101
//                - 影响： xxx11111x
//
//        影响的库存是确保本次选座之前没卖过票，还能与本次购买的区间有交集的区间
//
//                startIndex = 4
//
//        endIndex = 7
//
//        minStartIndex = startIndex -（往前碰到的最后一个 0）
//
//        maxStartIndex = endIndex - 1
//
//        minEndIndex = startIndex + 1
//
//        maxEndIndex = endIndex +（往后碰到的最后一个 0）

            // 获取车票的起始索引
            Integer startIndex = daily_Train_Ticket.getStartIndex();
            // 获取车票的结束索引
            Integer endIndex = daily_Train_Ticket.getEndIndex();
            // 将已售座位字符串转换为字符数组
            char[] sellChars = seat.getSell().toCharArray();
            // 计算最大的起始索引，即结束索引前的一个位置
            Integer maxStartIndex = endIndex - 1;
            // 计算最小的结束索引，即起始索引后的一个位置
            Integer minEndIndex = startIndex + 1;
            // 初始化最小的起始索引为0
            Integer minStartIndex = 0;
            // 从起始索引向前遍历，寻找最靠前的已售座位
            for (int i = startIndex-1; i >= 0; i--) {
                char aChar = sellChars[i];
                // 当找到已售座位时，更新最小起始索引，并跳出循环
                if (aChar == '1') {
                    minStartIndex = i + 1;
                    break;
                }
            }
            LOG.info("影响的出发站区间为"+minStartIndex+"-"+maxStartIndex);
            // 初始化最大的结束索引为已售座位字符串的最后一个位置
            Integer maxEndIndex = seat.getSell().length();
            // 从结束索引向后遍历，寻找最靠后的已售座位
            for (int i = endIndex; i < seat.getSell().length(); i++) {
                char aChar = sellChars[i];
                // 当找到已售座位时，更新最大结束索引，并跳出循环
                if (aChar == '1') {
                    maxEndIndex = i;
                    break;
                }
            }
            LOG.info("影响的到达站区间为"+minEndIndex+"-"+maxEndIndex);
            // 更新余票详情表
            LOG.info("传入的参数有{},{},{},{},{},{},{}",seat.getDate(),
                    seat.getTrainCode(),
                    seat.getSeatType(),
                    minStartIndex,
                    maxStartIndex,
                    minEndIndex,
                    maxEndIndex);
            cust_Daily_Train_TicketMapper.updateCountBySell(
                seat.getDate(),
                seat.getTrainCode(),
                seat.getSeatType(),
                minStartIndex,
                maxStartIndex,
                minEndIndex,
                maxEndIndex
            );
        }

    }

}