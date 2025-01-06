package com.guo.business.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.util.EnumUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.guo.business.TypeEnum.ConfirmOrderStatusEnum;
import com.guo.business.TypeEnum.TrainCarriageTypeEnum;
import com.guo.business.TypeEnum.TrainSeatTypeEnum;
import com.guo.business.domain.*;
import com.guo.business.mapper.Confirm_OrderMapper;
import com.guo.business.req.Confirm_OrderDoReq;
import com.guo.business.req.Confirm_OrderQueryReq;
import com.guo.business.req.Confirm_OrderSaveReq;
import com.guo.business.req.Confirm_OrderTicketReq;
import com.guo.business.resp.Confirm_OrderQueryResp;
import com.guo.common.context.LoginMemberContext;
import com.guo.common.exception.ExceptionConsole;
import com.guo.common.exception.ExceptionEnum;
import com.guo.common.resp.PageResp;
import com.guo.common.util.SnowUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 确认订单服务类
 */
@Service
public class Confirm_OrderService {

    private static final Logger LOG = LoggerFactory.getLogger(Confirm_OrderService.class);

    @Resource
    private Confirm_OrderMapper confirm_OrderMapper;
    @Resource
    private Daily_Train_TicketService daily_Train_TicketService;
    @Resource
    private Daily_Train_CarriageService Daily_Train_CarriageService;
    @Resource
    private Daily_Train_SeatService Daily_Train_SeatService;

    /**
     * 保存确认订单信息
     * @param req 确认订单保存请求对象
     */
    public void save(Confirm_OrderSaveReq req) {
        DateTime now = DateTime.now();
        Confirm_Order confirm_Order = BeanUtil.copyProperties(req, Confirm_Order.class);
        // 根据订单ID是否为空判断是插入还是更新操作
        if (ObjectUtil.isNull(confirm_Order.getId())) {
            confirm_Order.setId(SnowUtil.getSnowflakeNextId());
            confirm_Order.setCreateTime(now);
            confirm_Order.setUpdateTime(now);
            confirm_OrderMapper.insert(confirm_Order);
        } else {
            confirm_Order.setUpdateTime(now);
            confirm_OrderMapper.updateByPrimaryKey(confirm_Order);
        }
    }

    /**
     * 查询确认订单列表
     * @param req 确认订单查询请求对象
     * @return 分页响应对象
     */
    public PageResp<Confirm_OrderQueryResp> QueryList(Confirm_OrderQueryReq req) {
        Confirm_OrderExample confirm_OrderExample = new Confirm_OrderExample();
        confirm_OrderExample.setOrderByClause("id desc");
        Confirm_OrderExample.Criteria criteria = confirm_OrderExample.createCriteria();

        LOG.info("查询页码：{}", req.getPage());
        LOG.info("每页条数：{}", req.getSize());
        PageHelper.startPage(req.getPage(), req.getSize());
        List<Confirm_Order> confirm_OrderList = confirm_OrderMapper.selectByExample(confirm_OrderExample);

        PageInfo<Confirm_Order> pageInfo = new PageInfo<>(confirm_OrderList);
        LOG.info("总行数：{}", pageInfo.getTotal());
        LOG.info("总页数：{}", pageInfo.getPages());

        List<Confirm_OrderQueryResp> list = BeanUtil.copyToList(confirm_OrderList, Confirm_OrderQueryResp.class);

        PageResp<Confirm_OrderQueryResp> pageResp = new PageResp<>();
        pageResp.setTotal(pageInfo.getTotal());
        pageResp.setList(list);
        return pageResp;
    }

    /**
     * 删除确认订单
     * @param id 确认订单ID
     */
    public void delete(Long id) {
        confirm_OrderMapper.deleteByPrimaryKey(id);
    }

    /**
     * 执行存储确认订单操作
     * @param req 确认订单执行请求对象
     */
    public void DoStorage(Confirm_OrderDoReq req) {
        //车次是否存在

        //保存确认订单表
        DateTime now = DateTime.now();
        Confirm_Order confirm_Order = new Confirm_Order();
        List<Confirm_OrderTicketReq> tickets = req.getTickets();
        confirm_Order.setId(SnowUtil.getSnowflakeNextId());
        confirm_Order.setMemberId(LoginMemberContext.getId());
        Date date = req.getDate();
        String trainCode = req.getTrainCode();
        String start = req.getStart();
        String end = req.getEnd();
        confirm_Order.setDate(date);
        confirm_Order.setTrainCode(trainCode);
        confirm_Order.setStart(start);
        confirm_Order.setEnd(end);
        confirm_Order.setDailyTrainTicketId(req.getDailyTrainTicketId());
        confirm_Order.setStatus(ConfirmOrderStatusEnum.INIT.getCode());

        confirm_Order.setTickets(JSON.toJSONString(tickets));
        confirm_Order.setCreateTime(now);
        confirm_Order.setUpdateTime(now);
        confirm_OrderMapper.insert(confirm_Order);
        //查出余票数据，需要得到真实库存
        Daily_Train_Ticket dailyTrainTicket = daily_Train_TicketService.selectByUnique(date, trainCode, start, end);
        LOG.info("查出余票信息记录：{}", dailyTrainTicket);
        //预扣库存，并判断是否余票足够
        reduceTickets(req, dailyTrainTicket);
        //计算相对第一个座位的偏移值
        // 比如选择的是C1,D2，则偏移值是：[0,5]
        // 比如选择的是A1,B1,C1，则偏移值是：[0,1,2]
        Confirm_OrderTicketReq confirmOrderTicketReq = tickets.get(0);
        if (StrUtil.isNotBlank(confirmOrderTicketReq.getSeat())) {
            LOG.info("选座：{}", confirmOrderTicketReq.getSeat());
            // 查出本次选座的座位类型都有哪些列，用于计算所选座位与第一个座位的偏离值
            List<TrainSeatTypeEnum> colsByType = TrainSeatTypeEnum.getColsByType(confirmOrderTicketReq.getSeatTypeCode());
            LOG.info("根据选座的座位类型，筛选出所有的列：{}", colsByType);
            // 组成和前端两排选座一样的列表，用于作参照的座位列表，例：referSeatList = {A1, C1, D1, F1, A2, C2, D2, F2}
            // 例：A1, C1, D1, F1, A2, C2, D2, F2 C1到D2的绝对偏移值是：[1,6],得到的相对值就是[0,5]
            List<String> list = new ArrayList<>();
            for (int i = 1; i <= 2; i++) {
                for (TrainSeatTypeEnum TrainSeatTypeEnum : colsByType) {
                    list.add(TrainSeatTypeEnum.getCode() + i);

                }
            }
            LOG.info("用于做参照的俩排座位：{}", list);
            // 计算相对偏移值
            List<Integer> abolutOffsetList = new ArrayList<>();
            List<Integer> OffsetList = new ArrayList<>();
            for (Confirm_OrderTicketReq ticket : tickets) {
                String seat = ticket.getSeat();
                int index = list.indexOf(seat);
                abolutOffsetList.add(index);
                LOG.info("座位：{}，绝对偏移值：{}", seat, index);
            }
            LOG.info("计算得到所有座位的绝对偏移值：{}", abolutOffsetList);
            for (Integer index : abolutOffsetList) {
                if (index == 0) {
                    //第一个座位，不用偏移
                    continue;
                }
                //计算相对偏移值
                int offset = index - abolutOffsetList.get(0);
                OffsetList.add(offset);
            }
            LOG.info("计算得到所有座位的相对偏移值：{}", OffsetList);
            GetSeat(date,
                    trainCode,
                    confirmOrderTicketReq.getSeatTypeCode(),
                    confirmOrderTicketReq.getSeat().split("")[0], // 拿取的是A,C,D,F,而不是A1,B2这些
                    OffsetList);
        } else {
            LOG.info("不选座");
            GetSeat(date,
                    trainCode,
                    confirmOrderTicketReq.getSeatTypeCode(),
                    null,
                    null);
        }
    }

    /**
     * 获取指定条件下的座位信息
     * @param date 日期
     * @param trainCode 车次代码
     * @param seatType 座位类型代码
     * @param column 座位列
     * @param OffsetList 座位偏移列表
     */
    private void GetSeat(Date date, String trainCode, String seatType,String column,List<Integer> OffsetList){
        List<Daily_Train_Carriage> dailyTrainCarriages = Daily_Train_CarriageService.selectBySeatType(date, trainCode, seatType);
        LOG.info("共{}查出车厢信息记录", dailyTrainCarriages.size());
        //一个车厢一个车厢的获取座位数据
        for (Daily_Train_Carriage dailyTrainCarriage : dailyTrainCarriages) {
            LOG.info("车厢【{}】，获取座位数据", dailyTrainCarriage.getIndex());
            List<Daily_Train_Seat> dailyTrainSeats = Daily_Train_SeatService
                    .selectByCarriage(date, trainCode, dailyTrainCarriage.getIndex());
            LOG.info("车厢【{}】，共{}个座位信息记录", dailyTrainCarriage.getIndex(), dailyTrainSeats.size());

        }
    }

    /**
     * 减少余票数量
     * @param req 确认订单执行请求对象
     * @param dailyTrainTicket 每日列车票对象
     */
    private static void reduceTickets(Confirm_OrderDoReq req, Daily_Train_Ticket dailyTrainTicket) {
        for (Confirm_OrderTicketReq ticket : req.getTickets()) {
            String seatTypeCode = ticket.getSeatTypeCode();
            //获取枚举
            TrainCarriageTypeEnum seatTypeEnum = EnumUtil.getBy(TrainCarriageTypeEnum::getCode, seatTypeCode);
            //根据枚举类型，判断余票
            switch (seatTypeEnum) {
                case YDZ:
                    int CountLeft = dailyTrainTicket.getYdz() - 1;
                    if (CountLeft < 0) {
                        throw new ExceptionConsole(ExceptionEnum.TICKET_NOT_ENOUGH);
                    }
                    dailyTrainTicket.setYdz(CountLeft);
                    break;
                case EDZ:
                    int CountLeft1 = dailyTrainTicket.getEdz() - 1;
                    if (CountLeft1 < 0) {
                        throw new ExceptionConsole(ExceptionEnum.TICKET_NOT_ENOUGH);
                    }
                    dailyTrainTicket.setEdz(CountLeft1);
                    break;
                case RW:
                    int CountLeft2 = dailyTrainTicket.getRw() - 1;
                    if (CountLeft2 < 0) {
                        throw new ExceptionConsole(ExceptionEnum.TICKET_NOT_ENOUGH);
                    }
                    dailyTrainTicket.setRw(CountLeft2);
                    break;
                case YW:
                    int CountLeft3 = dailyTrainTicket.getYw() - 1;
                    if (CountLeft3 < 0) {
                        throw new ExceptionConsole(ExceptionEnum.TICKET_NOT_ENOUGH);
                    }
                    dailyTrainTicket.setYw(CountLeft3);
                    break;
            }
        }
    }

}
