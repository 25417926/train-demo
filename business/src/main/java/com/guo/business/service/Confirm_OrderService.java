package com.guo.business.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.util.EnumUtil;
import cn.hutool.core.util.NumberUtil;
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
    @Resource
    private After_Confirm_OrderService After_Confirm_OrderService;
    /**
     * 保存确认订单信息
     *
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
     *
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
     *
     * @param id 确认订单ID
     */
    public void delete(Long id) {
        confirm_OrderMapper.deleteByPrimaryKey(id);
    }

    /**
     * 执行存储确认订单操作
     *
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
        //创建一个存放最终选票结果的list
        List<Daily_Train_Seat> finalSeatList = new ArrayList<>();
        //创建存放tickets的长度
        int ticketSize = tickets.size();
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
            GetSeat(finalSeatList,
                    date,
                    trainCode,
                    confirmOrderTicketReq.getSeatTypeCode(),
                    confirmOrderTicketReq.getSeat().split("")[0], // 拿取的是A,C,D,F,而不是A1,B2这些
                    OffsetList,
                    dailyTrainTicket.getStartIndex(),
                    dailyTrainTicket.getEndIndex()

                    );
        } else {
            LOG.info("不选座");
            GetSeat(finalSeatList,
                    date,
                    trainCode,
                    confirmOrderTicketReq.getSeatTypeCode(),
                    null,
                    null,
                    dailyTrainTicket.getStartIndex(),
                    dailyTrainTicket.getEndIndex()

                   );
        }
        LOG.info("最终选座结果：{}", finalSeatList);
        //
        After_Confirm_OrderService.afterDoConfirm(dailyTrainTicket,finalSeatList);
    }

    /**
     * 根据指定的列车和座位类型获取座位信息
     * 此方法首先根据日期、列车代码和座位类型查询车厢信息，
     * 然后遍历每个车厢的座位信息，根据列值和偏移量选择合适的座位
     *
     * @param date       日期，用于查询特定日期的列车座位信息
     * @param trainCode  列车代码，标识特定的列车
     * @param seatType   座位类型，用于筛选车厢和座位
     * @param column     座位列值，用于进一步筛选座位
     * @param OffsetList 偏移量列表，用于选择相邻座位
     * @param startIndex 起始索引，用于偏移计算
     * @param endIndex   结束索引，用于偏移计算
     */

    private void GetSeat(List<Daily_Train_Seat> finalSeatList, Date date, String trainCode, String seatType, String column, List<Integer> offsetList, Integer startIndex, Integer endIndex) {
        List<Daily_Train_Seat> getSeatList = new ArrayList<>();
        List<Daily_Train_Carriage> carriageList = Daily_Train_CarriageService.selectBySeatType(date, trainCode, seatType);
        LOG.info("共查出{}个符合条件的车厢", carriageList.size());

        // 一个车箱一个车箱的获取座位数据
        for (Daily_Train_Carriage dailyTrainCarriage : carriageList) {
            LOG.info("开始从车厢{}选座", dailyTrainCarriage.getIndex());
            getSeatList = new ArrayList<>();
            List<Daily_Train_Seat> seatList = Daily_Train_SeatService.selectByCarriage(date, trainCode, dailyTrainCarriage.getIndex());
            LOG.info("车厢{}的座位数：{}", dailyTrainCarriage.getIndex(), seatList.size());
            for (int i = 0; i < seatList.size(); i++) {
                // 获取座位信息
                Daily_Train_Seat Daily_Train_Seat = seatList.get(i);
                // 获取座位索引
                Integer seatIndex = Daily_Train_Seat.getCarriageSeatIndex();
                // 获取座位列号
                String col = Daily_Train_Seat.getCol();

                // 判断当前座位不能被选中过
                boolean alreadyChooseFlag = false;
                for (Daily_Train_Seat finalSeat : finalSeatList){
                    // 如果当前座位被选中过，则跳过该座位
                    if (finalSeat.getId().equals(Daily_Train_Seat.getId())) {
                        alreadyChooseFlag = true;
                        break;
                    }
                }
                // 如果当前座位被选中过，则跳过该座位
                if (alreadyChooseFlag) {
                    LOG.info("座位{}被选中过，不能重复选中，继续判断下一个座位", seatIndex);
                    continue;
                }

                // 判断column，有值的话要比对列号
                if (StrUtil.isBlank(column)) {
                    LOG.info("无选座");
                } else {
                    // 判断列号
                    if (!column.equals(col)) {
                        LOG.info("座位{}列值不对，继续判断下一个座位，当前列值：{}，目标列值：{}", seatIndex, col, column);
                        continue;
                    }
                }
                // 判断座位是否可售
                boolean isChoose = callSell(Daily_Train_Seat, startIndex, endIndex);
                if (isChoose) {
                    LOG.info("选中座位");
                    getSeatList.add(Daily_Train_Seat);
                } else {
                    continue;
                }

                // 根据offset选剩下的座位
                boolean isGetAllOffsetSeat = true;
                // 判断是否已经获取到了所有座位
                //  判断是否有偏移值，有就进行偏移量选座
                if (CollUtil.isNotEmpty(offsetList)) {
                    LOG.info("有偏移值：{}，校验偏移的座位是否可选", offsetList);
                    // 从索引1开始，索引0就是当前已选中的票
                    for (int j = 1; j < offsetList.size(); j++) {
                        // 偏移值
                        Integer offset = offsetList.get(j);
                        // 座位在库的索引是从1开始
                        // int nextIndex = seatIndex + offset - 1;
                        int nextIndex = i + offset;

                        // 有选座时，一定是在同一个车箱
                        if (nextIndex >= seatList.size()) {
                            LOG.info("座位{}不可选，偏移后的索引超出了这个车箱的座位数", nextIndex);
                            isGetAllOffsetSeat = false;
                            break;
                        }
                        //拿取到以偏移值为索引的值
                        // 获取座位信息
                        Daily_Train_Seat nextDaily_Train_Seat = seatList.get(nextIndex);
                        //将刚拿到的座位信息数据和起点站台加终点站去判断座位是否可售
                        boolean isChooseNext = callSell(nextDaily_Train_Seat, startIndex, endIndex);
                        // 判断是否可选
                        if (isChooseNext) {
                            LOG.info("座位{}被选中", nextDaily_Train_Seat.getCarriageSeatIndex());
                            getSeatList.add(nextDaily_Train_Seat);
                        } else {
                            LOG.info("座位{}不可选", nextDaily_Train_Seat.getCarriageSeatIndex());
                            isGetAllOffsetSeat = false;
                            break;
                        }
                    }
                }
                // 如果选完偏移值，但中间有座位不可选，则重置getSeatList，继续选下一个座位
                if (!isGetAllOffsetSeat) {
                    getSeatList = new ArrayList<>();
                    continue;
                }

                // 保存选好的座位
                finalSeatList.addAll(getSeatList);
                return;
            }
        }
    }


    /**
     * 计算某座位在区间内是否可卖
     * 例：sell=10001，本次购买区间站1~4，则区间已售000
     * 全部是0，表示这个区间可买；只要有1，就表示区间内已售过票
     * <p>
     * 选中后，要计算购票后的sell，比如原来是10001，本次购买区间站1~4
     * 方案：构造本次购票造成的售卖信息01110，和原sell 10001按位或，最终得到11111
     *
     * @return
     */
    private static boolean callSell(Daily_Train_Seat Daily_Train_Seat, Integer startIndex, Integer endIndex) {
        //10001 00000
        String sell = Daily_Train_Seat.getSell();
        //000  000
        String sellPart = sell.substring(startIndex, endIndex);
        if (Integer.parseInt(sellPart) > 0) {
            LOG.info("座位{}在本次车站{}-{}区间已售，不可购买", Daily_Train_Seat.getCarriageSeatIndex(), startIndex, endIndex);
            return false;
        } else {
            LOG.info("座位{}在本次车站{}-{}区间未售，可购买", Daily_Train_Seat.getCarriageSeatIndex(), startIndex, endIndex);
            // 构造本次购票造成的售卖信息01110，和原sell 10001按位或，最终得到11111
            //111  111
            String curSell = sellPart.replace("0", "1");
            //0111 0111 从头补0，补到一共4位，也就是endIndex的值的位数
            curSell = StrUtil.fillBefore(curSell, '0', endIndex);
            //01110  01110 补到一共5位，也就是sell.length()的值的位数
            curSell = StrUtil.fillAfter(curSell, '0', sell.length());
            // 当前区间售票信息curSell 01110与库里的已售信息sell 00001按位或，即可得到该座位卖出此票后的售票详情
            // 按位与'&',按位或'|'，或'||'，与'&&'
            //15(01111) 14( 01110 | 00000 = 01110)
            int newSellInt = NumberUtil.binaryToInt(curSell) | NumberUtil.binaryToInt(sell);
            //转为字符串时第一位为零不会读取
            //1111 1110
            String newSell = NumberUtil.getBinaryStr(newSellInt);
            //需要再次进行补零，防止出现漏0的情况
            //01111 01110
            newSell = StrUtil.fillBefore(newSell, '0', sell.length());
            LOG.info("座位{}被选中，原售票信息：{}，车站区间：{}~{}，即：{}，最终售票信息：{}"
                    , Daily_Train_Seat.getCarriageSeatIndex(), sell, startIndex, endIndex, curSell, newSell);
            Daily_Train_Seat.setSell(newSell);
            return true;
        }

    }

    /**
     * 减少余票数量
     *
     * @param req              确认订单执行请求对象
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
