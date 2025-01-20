package com.guo.business.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.guo.business.domain.*;
import com.guo.business.mapper.Daily_Train_SeatMapper;
import com.guo.business.req.Daily_Train_SeatQueryReq;
import com.guo.business.req.Daily_Train_SeatSaveReq;
import com.guo.business.resp.Daily_Train_SeatQueryResp;
import com.guo.common.resp.PageResp;
import com.guo.common.util.SnowUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

@Service
public class Daily_Train_SeatService {

    private static final Logger LOG = LoggerFactory.getLogger(Daily_Train_SeatService.class);

    @Resource
    private Daily_Train_SeatMapper daily_Train_SeatMapper;
    @Resource
    private train_seatService train_seatService;
    @Resource
    private Train_StationService Train_StationService;


    public void save(Daily_Train_SeatSaveReq req) {
        DateTime now = DateTime.now();
        Daily_Train_Seat daily_Train_Seat = BeanUtil.copyProperties(req, Daily_Train_Seat.class);
        if (ObjectUtil.isNull(daily_Train_Seat.getId())) {
            daily_Train_Seat.setId(SnowUtil.getSnowflakeNextId());
            daily_Train_Seat.setCreateTime(now);
            daily_Train_Seat.setUpdateTime(now);
            daily_Train_SeatMapper.insert(daily_Train_Seat);
        } else {
            daily_Train_Seat.setUpdateTime(now);
            daily_Train_SeatMapper.updateByPrimaryKey(daily_Train_Seat);
        }
    }

    public PageResp<Daily_Train_SeatQueryResp> QueryList(Daily_Train_SeatQueryReq req) {
        Daily_Train_SeatExample daily_Train_SeatExample = new Daily_Train_SeatExample();
        daily_Train_SeatExample.setOrderByClause("date desc,train_code asc,`carriage_index` asc,`carriage_seat_index` asc");
        Daily_Train_SeatExample.Criteria criteria = daily_Train_SeatExample.createCriteria();
        if (ObjectUtil.isNotNull(req.getTrainCode())) {
            criteria.andTrainCodeEqualTo(req.getTrainCode());
        }
        LOG.info("查询页码：{}", req.getPage());
        LOG.info("每页条数：{}", req.getSize());
        PageHelper.startPage(req.getPage(), req.getSize());
        List<Daily_Train_Seat> daily_Train_SeatList = daily_Train_SeatMapper.selectByExample(daily_Train_SeatExample);

        PageInfo<Daily_Train_Seat> pageInfo = new PageInfo<>(daily_Train_SeatList);
        LOG.info("总行数：{}", pageInfo.getTotal());
        LOG.info("总页数：{}", pageInfo.getPages());

        List<Daily_Train_SeatQueryResp> list = BeanUtil.copyToList(daily_Train_SeatList, Daily_Train_SeatQueryResp.class);

        PageResp<Daily_Train_SeatQueryResp> pageResp = new PageResp<>();
        pageResp.setTotal(pageInfo.getTotal());
        pageResp.setList(list);
        return pageResp;
    }

    public void delete(Long id) {
        daily_Train_SeatMapper.deleteByPrimaryKey(id);
    }

    @Transactional
    public void genDaily(Date date, String trainCode) {
        LOG.info("生成日期【{}】车次【{}】的座位信息开始", DateUtil.formatDate(date), trainCode);
        DateTime now = DateTime.now();
        Daily_Train_SeatExample Daily_Train_SeatExample = new Daily_Train_SeatExample();
        Daily_Train_SeatExample.Criteria criteria = Daily_Train_SeatExample.createCriteria();
        criteria
                .andDateEqualTo(date)
                .andTrainCodeEqualTo(trainCode);
        //删某日除该车次座位数据
        daily_Train_SeatMapper.deleteByExample(Daily_Train_SeatExample);
        //查出某日该车次的车站数据
        List<Train_Station> train_stations = Train_StationService.selectByTrainCode(trainCode);
        //查出某日该车次的座位数据
        List<train_seat> trainStations = train_seatService.selectByTrainCode(trainCode);
        String sell = StrUtil.fillBefore("", '0', train_stations.size() - 1);
        if (CollUtil.isEmpty(trainStations)) {
            LOG.info("该车次没有车站数据，日期【{}】车次【{}】的座位信息生成完毕", DateUtil.formatDate(date), trainCode);
            return;
        }
        //生成该车次座位的数据
        for (train_seat train : trainStations) {
            Daily_Train_Seat Daily_Train_Seat = BeanUtil.copyProperties(train, Daily_Train_Seat.class);
            Daily_Train_Seat.setId(SnowUtil.getSnowflakeNextId());
            Daily_Train_Seat.setCreateTime(now);
            Daily_Train_Seat.setUpdateTime(now);
            Daily_Train_Seat.setSell(sell);
            Daily_Train_Seat.setDate(date);
            daily_Train_SeatMapper.insert(Daily_Train_Seat);
            LOG.info("生成车次【{}】厢序列=【{}】的座位信息完成", trainCode, Daily_Train_Seat.getCarriageIndex());
            LOG.info("生成日期【{}】车次【{}】的座位信息完成", DateUtil.formatDate(date), trainCode);
        }

    }

    /**
     * 根据日期、车次和席别统计座位数量
     *
     * @param date      日期，用于筛选特定日期的列车座位信息
     * @param trainCode 车次，用于筛选特定车次的列车座位信息
     * @param seatType  席别，用于筛选特定席别的列车座位信息
     * @return 座位数量，如果找不到匹配的座位信息则返回-1
     */
    public int CountSeats(Date date, String trainCode) {
        return CountSeats(date, trainCode, null);
    }
    public int CountSeats(Date date, String trainCode, String seatType) {
        // 创建一个Daily_Train_SeatExample对象，用于设置查询条件
        Daily_Train_SeatExample Daily_Train_SeatExample = new Daily_Train_SeatExample();
        // 创建一个Criteria对象，用于定义查询的条件
        Daily_Train_SeatExample.Criteria criteria = Daily_Train_SeatExample.createCriteria();
        // 设置查询条件：日期、车次和类别
        criteria
                .andDateEqualTo(date)
                .andTrainCodeEqualTo(trainCode);
               if(StrUtil.isNotBlank(seatType)){
                   criteria.andSeatTypeEqualTo(seatType);
               }
        // 使用daily_Train_SeatMapper的countByExample方法，根据设置的条件统计符合条件的座位数量
        long l = daily_Train_SeatMapper.countByExample(Daily_Train_SeatExample);
        // 如果统计结果为0，表示没有找到符合条件的座位信息，返回-1
        if (l == 0L) {
            return -1;
        }
        // 将统计结果转换为int类型并返回
        return (int) l;
    }

    public List<Daily_Train_Seat> selectByCarriage(Date date, String trainCode, Integer carriageIndex) {
        Daily_Train_SeatExample daily_Train_SeatExample = new Daily_Train_SeatExample();
        // 设置排序规则,按照座位索引排序
        daily_Train_SeatExample.setOrderByClause("`carriage_seat_index` asc");
        Daily_Train_SeatExample.Criteria criteria = daily_Train_SeatExample.createCriteria();
        criteria
                .andDateEqualTo(date)
                .andTrainCodeEqualTo(trainCode)
                .andCarriageIndexEqualTo(carriageIndex);
        return daily_Train_SeatMapper.selectByExample(daily_Train_SeatExample);
    }
    @Transactional
    public int SeatForUpdate(Daily_Train_Seat daily_Train_Seat) {
        Daily_Train_Seat seat =new Daily_Train_Seat();
        seat.setId(daily_Train_Seat.getId());
        seat.setSell(daily_Train_Seat.getSell());
        seat.setUpdateTime(new Date());
        daily_Train_SeatMapper.updateByPrimaryKeySelective(seat);
        return daily_Train_SeatMapper.updateByPrimaryKeySelective(seat);
    }
}
