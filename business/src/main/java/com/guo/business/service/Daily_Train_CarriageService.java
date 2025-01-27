package com.guo.business.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ObjectUtil;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.guo.business.TypeEnum.TrainSeatTypeEnum;
import com.guo.business.domain.*;
import com.guo.business.mapper.Daily_Train_CarriageMapper;
import com.guo.business.req.Daily_Train_CarriageQueryReq;
import com.guo.business.req.Daily_Train_CarriageSaveReq;
import com.guo.business.resp.Daily_Train_CarriageQueryResp;
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
public class Daily_Train_CarriageService {

    private static final Logger LOG = LoggerFactory.getLogger(Daily_Train_CarriageService.class);

    @Resource
    private Daily_Train_CarriageMapper daily_Train_CarriageMapper;
    @Resource
    private Train_CarriageService Train_CarriageService;
    public void save(Daily_Train_CarriageSaveReq req) {
        DateTime now = DateTime.now();
        // 根据座位类型获取列数，并计算总座位数
        req.setColCount(TrainSeatTypeEnum.getColsByType(req.getSeatType()).size());
        req.setSeatCount(req.getRowCount() * req.getColCount());
        Daily_Train_Carriage daily_Train_Carriage = BeanUtil.copyProperties(req, Daily_Train_Carriage.class);
        if (ObjectUtil.isNull(daily_Train_Carriage.getId())) {
            daily_Train_Carriage.setId(SnowUtil.getSnowflakeNextId());
            daily_Train_Carriage.setCreateTime(now);
            daily_Train_Carriage.setUpdateTime(now);
            daily_Train_CarriageMapper.insert(daily_Train_Carriage);
        } else {
            daily_Train_Carriage.setUpdateTime(now);
            daily_Train_CarriageMapper.updateByPrimaryKey(daily_Train_Carriage);
        }
    }

    public PageResp<Daily_Train_CarriageQueryResp> QueryList(Daily_Train_CarriageQueryReq req) {
        Daily_Train_CarriageExample daily_Train_CarriageExample = new Daily_Train_CarriageExample();
        daily_Train_CarriageExample.setOrderByClause("date desc,train_code asc,`index` asc");
        Daily_Train_CarriageExample.Criteria criteria = daily_Train_CarriageExample.createCriteria();
        if (ObjectUtil.isNotNull(req.getDate())) {
            criteria.andDateEqualTo(req.getDate());
        }
        if (ObjectUtil.isNotEmpty(req.getTrainCode())) {
            criteria.andTrainCodeEqualTo(req.getTrainCode());
        }
        LOG.info("查询页码：{}", req.getPage());
        LOG.info("每页条数：{}", req.getSize());
        PageHelper.startPage(req.getPage(), req.getSize());
        List<Daily_Train_Carriage> daily_Train_CarriageList = daily_Train_CarriageMapper.selectByExample(daily_Train_CarriageExample);

        PageInfo<Daily_Train_Carriage> pageInfo = new PageInfo<>(daily_Train_CarriageList);
        LOG.info("总行数：{}", pageInfo.getTotal());
        LOG.info("总页数：{}", pageInfo.getPages());

        List<Daily_Train_CarriageQueryResp> list = BeanUtil.copyToList(daily_Train_CarriageList, Daily_Train_CarriageQueryResp.class);

        PageResp<Daily_Train_CarriageQueryResp> pageResp = new PageResp<>();
        pageResp.setTotal(pageInfo.getTotal());
        pageResp.setList(list);
        return pageResp;
    }

    public void delete(Long id) {
        daily_Train_CarriageMapper.deleteByPrimaryKey(id);
    }
    @Transactional
    public void genDaily(Date date, String trainCode) {
        LOG.info("生成日期【{}】车次【{}】的车厢信息开始", DateUtil.formatDate(date), trainCode);
        DateTime now = DateTime.now();
        Daily_Train_CarriageExample Daily_Train_CarriageExample = new Daily_Train_CarriageExample();
        Daily_Train_CarriageExample.Criteria criteria = Daily_Train_CarriageExample.createCriteria();
        criteria
                .andDateEqualTo(date)
                .andTrainCodeEqualTo(trainCode);
        //删某日除该车次车站数据
        daily_Train_CarriageMapper.deleteByExample(Daily_Train_CarriageExample);
        //查出某日该车次的车站数据
        List<Train_Carriage> trainStations = Train_CarriageService.selectByTrainCode(trainCode);
        if(CollUtil.isEmpty(trainStations)){
            LOG.info("该车次没有车站数据，日期【{}】车次【{}】的车厢信息生成完毕", DateUtil.formatDate(date) , trainCode);
            return;
        }
        //生成该车次的数据
        for(Train_Carriage train :trainStations ){
            Daily_Train_Carriage Train_Carriage = BeanUtil.copyProperties(train, Daily_Train_Carriage.class);
            Train_Carriage.setId(SnowUtil.getSnowflakeNextId());
            Train_Carriage.setCreateTime(now);
            Train_Carriage.setUpdateTime(now);
            Train_Carriage.setDate(date);
            daily_Train_CarriageMapper.insert(Train_Carriage);

            LOG.info("生成日期【{}】车次【{}】的车厢信息完成", DateUtil.formatDate(date), trainCode);
        }

    }
    public List<Daily_Train_Carriage> selectBySeatType(Date date, String trainCode, String seatType){
        Daily_Train_CarriageExample example = new Daily_Train_CarriageExample();
        example.createCriteria()
                .andDateEqualTo(date)
                .andTrainCodeEqualTo(trainCode)
                .andSeatTypeEqualTo(seatType);
        return daily_Train_CarriageMapper.selectByExample(example);
    }
}
