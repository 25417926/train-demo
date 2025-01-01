package com.guo.business.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.guo.business.TypeEnum.TrainCarriageTypeEnum;
import com.guo.business.TypeEnum.TrainSeatTypeEnum;
import com.guo.business.domain.Train_Carriage;
import com.guo.business.domain.train_seat;
import com.guo.business.domain.train_seatExample;
import com.guo.business.mapper.train_seatMapper;
import com.guo.business.req.train_seatQueryReq;
import com.guo.business.req.train_seatSaveReq;
import com.guo.business.resp.train_seatQueryResp;
import com.guo.common.resp.PageResp;
import com.guo.common.util.SnowUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class train_seatService {

    private static final Logger LOG = LoggerFactory.getLogger(train_seatService.class);

    @Resource
    private train_seatMapper train_seatMapper;
    @Resource
    private Train_CarriageService trainCarriageService;
    public void save(train_seatSaveReq req) {
        DateTime now = DateTime.now();
        train_seat train_seat = BeanUtil.copyProperties(req, train_seat.class);
        if (ObjectUtil.isEmpty(train_seat.getId())) {
            train_seat.setId(SnowUtil.getSnowflakeNextId());
            train_seat.setCreateTime(now);
            train_seat.setUpdateTime(now);
            train_seatMapper.insert(train_seat);
        } else {
            train_seat.setUpdateTime(now);
            train_seatMapper.updateByPrimaryKey(train_seat);
        }
    }

    public PageResp<train_seatQueryResp> QueryList(train_seatQueryReq req) {
        train_seatExample train_seatExample = new train_seatExample();
        train_seatExample.setOrderByClause("train_code asc,`carriage_index` asc,`carriage_seat_index` asc");
        train_seatExample.Criteria criteria = train_seatExample.createCriteria();
        if (ObjectUtil.isNotEmpty(req.getTrainCode())) {
            criteria.andTrainCodeEqualTo(req.getTrainCode());
        }
        LOG.info("查询页码：{}", req.getPage());
        LOG.info("每页条数：{}", req.getSize());
        PageHelper.startPage(req.getPage(), req.getSize());
        List<train_seat> train_seatList = train_seatMapper.selectByExample(train_seatExample);

        PageInfo<train_seat> pageInfo = new PageInfo<>(train_seatList);
        LOG.info("总行数：{}", pageInfo.getTotal());
        LOG.info("总页数：{}", pageInfo.getPages());

        List<train_seatQueryResp> list = BeanUtil.copyToList(train_seatList, train_seatQueryResp.class).stream().map(
                resp -> {
                    String train_seatTypeEnum = TrainCarriageTypeEnum.fromCode(resp.getSeatType(), null).getDesc();
                    resp.setSeatType(train_seatTypeEnum);
                    return resp;
                }
        ).collect(Collectors.toList());

        PageResp<train_seatQueryResp> pageResp = new PageResp<>();
        pageResp.setTotal(pageInfo.getTotal());
        pageResp.setList(list);
        return pageResp;
    }
    //开启事务处理
    @Transactional
    public void genSeat(String trainCode) {
        //获取当前时间，用于记录创建和更新时间
        DateTime now = DateTime.now();
        //创建查询条件对象，用于后续的查询操作
        train_seatExample train_seatExample = new train_seatExample();
        //设置查询的排序条件，以便数据按一定顺序排列
        //train_seatExample.setOrderByClause("train_code asc,`carriage_index` asc,`carriage_seat_index` asc");
        //创建查询条件
        train_seatExample.Criteria criteria = train_seatExample.createCriteria();
        //判断是否有数据
        if(ObjectUtil.isNotEmpty(trainCode)) {
            //将车次填入example中，以便后续删除该车次的所有座位数据
            criteria.andTrainCodeEqualTo(trainCode);
            //删除车次的所有数据，为后续重新生成座位数据做准备
            train_seatMapper.deleteByExample(train_seatExample);
            //查询车次的所有车厢，以便后续为每个车厢生成座位数据
            List<Train_Carriage> carriageList =trainCarriageService.SelectByTrainCode(trainCode);
            //循环生成每个车厢的座位
            for(Train_Carriage train_Carriage:carriageList){
                //获取车厢的行数
                Integer rowCount = train_Carriage.getRowCount();
                //获取车厢的列数
                String seatType = train_Carriage.getSeatType();
                //获取车厢的座位类型
                List<TrainSeatTypeEnum> colsByType = TrainSeatTypeEnum.getColsByType(seatType);
                //初始化座位计数器
                int seatCount = 1;
                //循环生成车厢的座位
                for(int i=1;i<=rowCount;i++){
                    //循环生成车厢的列数
                    for(TrainSeatTypeEnum trainSeatTypeEnum:colsByType){
                        //创建座位数据并保存到数据库
                        train_seat train_seat = new train_seat();
                        //使用Snowflake算法生成唯一ID
                        train_seat.setId(SnowUtil.getSnowflakeNextId());
                        //设置车次
                        train_seat.setTrainCode(trainCode);
                        //设置车厢索引
                        train_seat.setCarriageIndex(train_Carriage.getIndex());
                        //设置行号，确保行号为两位数，不足两位前面补0
                        train_seat.setRow(StrUtil.fillBefore(String.valueOf(i), '0', 2));
                        //设置列号
                        train_seat.setCol(trainSeatTypeEnum.getCode());
                        //设置座位类型
                        train_seat.setSeatType(seatType);
                        //设置车厢内座位索引
                        train_seat.setCarriageSeatIndex(seatCount++);
                        //设置创建时间
                        train_seat.setCreateTime(now);
                        //设置更新时间
                        train_seat.setUpdateTime(now);
                        //将座位数据插入数据库
                        train_seatMapper.insert(train_seat);
                    }
                }
            }
        }
    }

    public void delete(Long id) {
        train_seatMapper.deleteByPrimaryKey(id);
    }
}
