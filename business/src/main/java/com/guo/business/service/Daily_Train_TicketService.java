package com.guo.business.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ObjectUtil;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.guo.business.TypeEnum.TrainCarriageTypeEnum;
import com.guo.business.TypeEnum.TrainTypeEnum;
import com.guo.business.domain.*;
import com.guo.business.inventory.InventoryKey;
import com.guo.business.mapper.Daily_Train_TicketMapper;
import com.guo.business.req.Daily_Train_TicketQueryReq;
import com.guo.business.resp.Daily_Train_TicketQueryResp;
import com.guo.business.waitlist.WaitlistKey;
import com.guo.common.resp.PageResp;
import com.guo.common.util.SnowUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 日列车票务服务类
 */
@Service
public class Daily_Train_TicketService {

    private static final Logger LOG = LoggerFactory.getLogger(Daily_Train_TicketService.class);

    @Resource
    private Daily_Train_TicketMapper daily_Train_TicketMapper;
    @Resource
    private Train_StationService Train_StationService;
    @Resource
    private Daily_Train_SeatService Daily_Train_SeatService;
    @Resource
    private StringRedisTemplate redis;

    /** 与 ydz/edz/rw/yw 一一对应的席别编码 */
    private static final String[] WAITLIST_SEAT_TYPES = {"1", "2", "3", "4"};

    /**
     * 查询列车票务信息列表
     *
     * @param req 查询请求对象，包含查询条件
     * @return 分页响应对象，包含查询结果
     */
    public PageResp<Daily_Train_TicketQueryResp> QueryList(Daily_Train_TicketQueryReq req) {
        Daily_Train_TicketExample daily_Train_TicketExample = new Daily_Train_TicketExample();
        daily_Train_TicketExample.setOrderByClause("id desc");
        Daily_Train_TicketExample.Criteria criteria = daily_Train_TicketExample.createCriteria();
        if (ObjectUtil.isNotNull(req.getDate())) {
            criteria.andDateEqualTo(req.getDate());
        }
        if (ObjectUtil.isNotEmpty(req.getTrainCode())) {
            criteria.andTrainCodeEqualTo(req.getTrainCode());
        }
        if (ObjectUtil.isNotEmpty(req.getStart())) {
            criteria.andStartEqualTo(req.getStart());
        }
        if (ObjectUtil.isNotEmpty(req.getEnd())) {
            criteria.andEndEqualTo(req.getEnd());
        }
        LOG.info("查询页码：{}", req.getPage());
        LOG.info("每页条数：{}", req.getSize());
        PageHelper.startPage(req.getPage(), req.getSize());
        List<Daily_Train_Ticket> daily_Train_TicketList = daily_Train_TicketMapper.selectByExample(daily_Train_TicketExample);

        PageInfo<Daily_Train_Ticket> pageInfo = new PageInfo<>(daily_Train_TicketList);
        LOG.info("总行数：{}", pageInfo.getTotal());
        LOG.info("总页数：{}", pageInfo.getPages());

        List<Daily_Train_TicketQueryResp> list = BeanUtil.copyToList(daily_Train_TicketList, Daily_Train_TicketQueryResp.class);

        PageResp<Daily_Train_TicketQueryResp> pageResp = new PageResp<>();
        pageResp.setTotal(pageInfo.getTotal());
        pageResp.setList(list);
        return pageResp;
    }

    /**
     * C 端余票查询：按票档扣除候补等待人数，避免"释放后显示充足、实际被候补优先消费"。
     *
     * <p>不修改库内余票，只在响应里折算可售数量并回传 waitlistCount，后台列表仍用 {@link #QueryList}。</p>
     */
    public PageResp<Daily_Train_TicketQueryResp> QueryListForMember(Daily_Train_TicketQueryReq req) {
        PageResp<Daily_Train_TicketQueryResp> pageResp = QueryList(req);
        adjustWaitlist(pageResp.getList());
        return pageResp;
    }

    private void adjustWaitlist(List<Daily_Train_TicketQueryResp> list) {
        if (CollUtil.isEmpty(list)) {
            return;
        }
        // 每行 4 个席别，一次 multiGet 拿完，避免逐行往返
        List<String> keys = new ArrayList<>(list.size() * WAITLIST_SEAT_TYPES.length);
        for (Daily_Train_TicketQueryResp row : list) {
            String dateText = InventoryKey.formatDate(row.getDate());
            for (String seatType : WAITLIST_SEAT_TYPES) {
                keys.add(WaitlistKey.count(WaitlistKey.bucket(
                        dateText, row.getTrainCode(), row.getStart(), row.getEnd(), seatType)));
            }
        }
        List<String> counts = redis.opsForValue().multiGet(keys);

        int index = 0;
        for (Daily_Train_TicketQueryResp row : list) {
            row.setYdzWaitlist(waitlistCount(counts, index++));
            row.setEdzWaitlist(waitlistCount(counts, index++));
            row.setRwWaitlist(waitlistCount(counts, index++));
            row.setYwWaitlist(waitlistCount(counts, index++));
            row.setYdz(available(row.getYdz(), row.getYdzWaitlist()));
            row.setEdz(available(row.getEdz(), row.getEdzWaitlist()));
            row.setRw(available(row.getRw(), row.getRwWaitlist()));
            row.setYw(available(row.getYw(), row.getYwWaitlist()));
        }
    }

    private static Integer waitlistCount(List<String> counts, int index) {
        if (counts == null || counts.size() <= index) {
            return 0;
        }
        String value = counts.get(index);
        if (value == null || value.trim().isEmpty()) {
            return 0;
        }
        return Integer.parseInt(value);
    }

    /** 可售余票 = 库内余票 - 候补等待人数，最低为 0 */
    private static Integer available(Integer total, Integer waiting) {
        if (total == null) {
            return null;
        }
        return Math.max(total - (waiting == null ? 0 : waiting), 0);
    }

    /**
     * 删除指定ID的列车票务信息
     *
     * @param id 要删除的列车票务信息的ID
     */
    public void delete(Long id) {
        daily_Train_TicketMapper.deleteByPrimaryKey(id);
    }

    /**
     * 生成每日列车票务信息
     *
     * @param daily_Train 日列车对象，包含列车基本信息
     * @param date 日期，用于生成特定日期的票务信息
     * @param trainCode 列车车次，用于生成特定列车的票务信息
     */
    @Transactional
    public void genDaily(Daily_Train daily_Train, Date date, String trainCode) {
        LOG.info("生成日期【{}】车次【{}】的站点信息开始", DateUtil.formatDate(date), trainCode);

        DateTime now = DateTime.now();
        Daily_Train_TicketExample Daily_Train_TicketExample = new Daily_Train_TicketExample();
        Daily_Train_TicketExample.Criteria criteria = Daily_Train_TicketExample.createCriteria();
        criteria
                .andDateEqualTo(date)
                .andTrainCodeEqualTo(trainCode);
        //删某日除该余票数据
        daily_Train_TicketMapper.deleteByExample(Daily_Train_TicketExample);
        //查询某车次的所有车站信息
        List<Train_Station> trainStations = Train_StationService.selectByTrainCode(trainCode);
        if (CollUtil.isEmpty(trainStations)) {
            LOG.info("该车次没有车站数据，日期【{}】车次【{}】的余票信息生成完毕", DateUtil.formatDate(date), trainCode);
            return;
        }
        //生成该车次的数据
        //单向循环嵌套
        for (int i = 0; i < trainStations.size(); i++) {
            Train_Station train_StationStart = trainStations.get(i);
            //初始里程
            BigDecimal km = BigDecimal.ZERO;
            for (int j = i + 1; j < trainStations.size(); j++) {
                Train_Station train_StationEnd = trainStations.get(j);
                km = km.add(train_StationEnd.getKm());
                Daily_Train_Ticket dailyTrainTicket = new Daily_Train_Ticket();
                dailyTrainTicket.setId(SnowUtil.getSnowflakeNextId());
                dailyTrainTicket.setDate(date);
                dailyTrainTicket.setTrainCode(trainCode);
                dailyTrainTicket.setStart(train_StationStart.getName());
                dailyTrainTicket.setStartPinyin(train_StationStart.getNamePinyin());
                dailyTrainTicket.setStartTime(train_StationStart.getOutTime());
                dailyTrainTicket.setStartIndex(train_StationStart.getIndex());
                dailyTrainTicket.setEnd(train_StationEnd.getName());
                dailyTrainTicket.setEndPinyin(train_StationEnd.getNamePinyin());
                dailyTrainTicket.setEndTime(train_StationEnd.getInTime());
                dailyTrainTicket.setEndIndex(train_StationEnd.getIndex());
                int Ydz = Daily_Train_SeatService.CountSeats(date, trainCode, TrainCarriageTypeEnum.YDZ.getCode());
                int Edz = Daily_Train_SeatService.CountSeats(date, trainCode, TrainCarriageTypeEnum.EDZ.getCode());
                int Rw = Daily_Train_SeatService.CountSeats(date, trainCode, TrainCarriageTypeEnum.RW.getCode());
                int Yw = Daily_Train_SeatService.CountSeats(date, trainCode, TrainCarriageTypeEnum.YW.getCode());
                //票价= km *  车次类型系数 * 座位类型系数
                BigDecimal TrainTypePrice = TrainTypeEnum.fromCode(daily_Train.getType(), null).getPrice();
                BigDecimal YdzPrice = km.multiply(TrainCarriageTypeEnum.YDZ.getPrice()).multiply(TrainTypePrice).setScale(2, RoundingMode.HALF_UP);
                BigDecimal EdzPrice = km.multiply(TrainCarriageTypeEnum.EDZ.getPrice()).multiply(TrainTypePrice).setScale(2, RoundingMode.HALF_UP);
                BigDecimal RwPrice = km.multiply(TrainCarriageTypeEnum.RW.getPrice()).multiply(TrainTypePrice).setScale(2, RoundingMode.HALF_UP);
                BigDecimal YwPrice = km.multiply(TrainCarriageTypeEnum.YW.getPrice()).multiply(TrainTypePrice).setScale(2, RoundingMode.HALF_UP);
                dailyTrainTicket.setYdz(Ydz);
                dailyTrainTicket.setYdzPrice(YdzPrice);
                dailyTrainTicket.setEdz(Edz);
                dailyTrainTicket.setEdzPrice(EdzPrice);
                dailyTrainTicket.setRw(Rw);
                dailyTrainTicket.setRwPrice(RwPrice);
                dailyTrainTicket.setYw(Yw);
                dailyTrainTicket.setYwPrice(YwPrice);

                dailyTrainTicket.setCreateTime(now);
                dailyTrainTicket.setUpdateTime(now);
                daily_Train_TicketMapper.insert(dailyTrainTicket);
            }
        }
        LOG.info("生成日期【{}】车次【{}】的余票信息生成完毕", DateUtil.formatDate(date), trainCode);


    }

    /**
     * 根据唯一条件查询列车票务信息
     *
     * @param date 日期
     * @param trainCode 列车车次
     * @param start 起始站
     * @param end 终点站
     * @return 查询到的列车票务信息对象，如果没有找到则返回null
     */
    public Daily_Train_Ticket selectByUnique(Date date, String trainCode, String start, String end) {
        Daily_Train_TicketExample Daily_Train_TicketExample = new Daily_Train_TicketExample();
        Daily_Train_TicketExample.Criteria criteria = Daily_Train_TicketExample.createCriteria();
        criteria
                .andDateEqualTo(date)
                .andTrainCodeEqualTo(trainCode)
                .andStartEqualTo(start)
                .andEndEqualTo(end);

        List<Daily_Train_Ticket> list = daily_Train_TicketMapper.selectByExample(Daily_Train_TicketExample);
        if (CollUtil.isEmpty(list)) {
            return null;
        } else {
            return list.get(0);
        }
    }
}

