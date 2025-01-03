package com.guo.business.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.util.ObjectUtil;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.guo.business.TypeEnum.TrainTypeEnum;
import com.guo.business.domain.Daily_Train;
import com.guo.business.domain.Daily_TrainExample;
import com.guo.business.domain.train;
import com.guo.business.mapper.Daily_TrainMapper;
import com.guo.business.req.Daily_TrainQueryReq;
import com.guo.business.req.Daily_TrainSaveReq;
import com.guo.business.resp.Daily_TrainQueryResp;
import com.guo.common.resp.PageResp;
import com.guo.common.util.SnowUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class Daily_TrainService {

    private static final Logger LOG = LoggerFactory.getLogger(Daily_TrainService.class);

    @Resource
    private Daily_TrainMapper daily_TrainMapper;
    @Resource
    private trainService trainService;
    @Resource
    private Daily_Train_StationService DailyTrainStationService;
    @Resource
    private Daily_Train_CarriageService Daily_Train_CarriageService;
    public void save(Daily_TrainSaveReq req) {
        DateTime now = DateTime.now();
        Daily_Train daily_Train = BeanUtil.copyProperties(req, Daily_Train.class);
        String type = TrainTypeEnum.fromCode(null,req.getType()).getCode();
        if (ObjectUtil.isNull(daily_Train.getId())) {

            daily_Train.setId(SnowUtil.getSnowflakeNextId());
            daily_Train.setType(type);
            daily_Train.setCreateTime(now);
            daily_Train.setUpdateTime(now);
            daily_TrainMapper.insert(daily_Train);
        } else {

            daily_Train.setType(type);
            daily_Train.setUpdateTime(now);
            daily_TrainMapper.updateByPrimaryKey(daily_Train);
        }
    }

    public PageResp<Daily_TrainQueryResp> QueryList(Daily_TrainQueryReq req) {
        Daily_TrainExample daily_TrainExample = new Daily_TrainExample();
        daily_TrainExample.setOrderByClause("date desc ,code asc");
        Daily_TrainExample.Criteria criteria = daily_TrainExample.createCriteria();
        if(ObjectUtil.isNotNull(req.getDate())) {
            criteria.andDateEqualTo(req.getDate());
        }
        if (ObjectUtil.isNotEmpty(req.getCode())) {
            criteria.andCodeEqualTo(req.getCode());
        }
        LOG.info("查询页码：{}", req.getPage());
        LOG.info("每页条数：{}", req.getSize());
        PageHelper.startPage(req.getPage(), req.getSize());
        List<Daily_Train> daily_TrainList = daily_TrainMapper.selectByExample(daily_TrainExample);

        PageInfo<Daily_Train> pageInfo = new PageInfo<>(daily_TrainList);
        LOG.info("总行数：{}", pageInfo.getTotal());
        LOG.info("总页数：{}", pageInfo.getPages());

        List<Daily_TrainQueryResp> list = BeanUtil.copyToList(daily_TrainList, Daily_TrainQueryResp.class)
                .stream().peek(
                        resp -> {
                            resp.setType(TrainTypeEnum.fromCode(resp.getType(),null).getDesc());
                        }
                ).collect(Collectors.toList());

        PageResp<Daily_TrainQueryResp> pageResp = new PageResp<>();
        pageResp.setTotal(pageInfo.getTotal());
        pageResp.setList(list);
        return pageResp;
    }

    public void delete(Long id) {
        daily_TrainMapper.deleteByPrimaryKey(id);
    }

    /**
     * 生成某日所以车次信息,包括车次，车厢等
     * @param date
     */
    public void gen_daily(Date date) {

        List<train> trainList = trainService.selectAll();
        if(CollUtil.isEmpty(trainList)){
            LOG.error("没有车次数据信息,任务结束");
            return;
        }
        for(train train : trainList){
            gen_dailyTrain(date,train);
        }

    }
    public void gen_dailyTrain(Date date,train train) {
        DateTime now = DateTime.now();
        Daily_TrainExample daily_TrainExample = new Daily_TrainExample();
        Daily_TrainExample.Criteria criteria = daily_TrainExample.createCriteria();
        criteria
                .andDateEqualTo(date)
                .andCodeEqualTo(train.getCode());
        //删除该车次已有数据
        daily_TrainMapper.deleteByExample(daily_TrainExample);
        //生成该车次的数据
        Daily_Train daily_Train = BeanUtil.copyProperties(train, Daily_Train.class);
        daily_Train.setId(SnowUtil.getSnowflakeNextId());
        daily_Train.setCreateTime(now);
        daily_Train.setUpdateTime(now);
        daily_Train.setDate(date);
        daily_TrainMapper.insert(daily_Train);
        //生成该车次车站的数据
        DailyTrainStationService.genDaily(date,train.getCode());
        //生成该车次车厢的数据
        Daily_Train_CarriageService.genDaily(date,train.getCode());
    }
}
