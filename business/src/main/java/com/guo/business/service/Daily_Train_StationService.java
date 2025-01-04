package com.guo.business.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ObjectUtil;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.guo.business.domain.Daily_Train_Station;
import com.guo.business.domain.Daily_Train_StationExample;
import com.guo.business.domain.Train_Station;
import com.guo.business.mapper.Daily_Train_StationMapper;
import com.guo.business.req.Daily_Train_StationQueryReq;
import com.guo.business.req.Daily_Train_StationSaveReq;
import com.guo.business.resp.Daily_Train_StationQueryResp;
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
public class Daily_Train_StationService {

    private static final Logger LOG = LoggerFactory.getLogger(Daily_Train_StationService.class);

    @Resource
    private Daily_Train_StationMapper daily_Train_StationMapper;
    @Resource
    private Train_StationService Train_StationService;

    public void save(Daily_Train_StationSaveReq req) {
        DateTime now = DateTime.now();
        Daily_Train_Station daily_Train_Station = BeanUtil.copyProperties(req, Daily_Train_Station.class);
        if (ObjectUtil.isNull(daily_Train_Station.getId())) {
            daily_Train_Station.setId(SnowUtil.getSnowflakeNextId());
            daily_Train_Station.setCreateTime(now);
            daily_Train_Station.setUpdateTime(now);
            daily_Train_StationMapper.insert(daily_Train_Station);
        } else {
            daily_Train_Station.setUpdateTime(now);
            daily_Train_StationMapper.updateByPrimaryKey(daily_Train_Station);
        }
    }

    public PageResp<Daily_Train_StationQueryResp> QueryList(Daily_Train_StationQueryReq req) {
        Daily_Train_StationExample daily_Train_StationExample = new Daily_Train_StationExample();
        daily_Train_StationExample.setOrderByClause("date desc,train_code asc,`index` asc");
        Daily_Train_StationExample.Criteria criteria = daily_Train_StationExample.createCriteria();
        if(ObjectUtil.isNotNull(req.getDate())) {
            criteria.andDateEqualTo(req.getDate());
        }
        if (ObjectUtil.isNotEmpty(req.getTrainCode())) {
            criteria.andTrainCodeEqualTo(req.getTrainCode());
        }
        LOG.info("查询页码：{}", req.getPage());
        LOG.info("每页条数：{}", req.getSize());
        PageHelper.startPage(req.getPage(), req.getSize());
        List<Daily_Train_Station> daily_Train_StationList = daily_Train_StationMapper.selectByExample(daily_Train_StationExample);

        PageInfo<Daily_Train_Station> pageInfo = new PageInfo<>(daily_Train_StationList);
        LOG.info("总行数：{}", pageInfo.getTotal());
        LOG.info("总页数：{}", pageInfo.getPages());

        List<Daily_Train_StationQueryResp> list = BeanUtil.copyToList(daily_Train_StationList, Daily_Train_StationQueryResp.class);

        PageResp<Daily_Train_StationQueryResp> pageResp = new PageResp<>();
        pageResp.setTotal(pageInfo.getTotal());
        pageResp.setList(list);
        return pageResp;
    }

    public void delete(Long id) {
        daily_Train_StationMapper.deleteByPrimaryKey(id);
    }
    @Transactional
    public void genDaily(Date date, String trainCode) {
        LOG.info("生成日期【{}】车次【{}】的站点信息开始", DateUtil.formatDate(date), trainCode);
        DateTime now = DateTime.now();
        Daily_Train_StationExample Daily_Train_StationExample = new Daily_Train_StationExample();
        Daily_Train_StationExample.Criteria criteria = Daily_Train_StationExample.createCriteria();
        criteria
                .andDateEqualTo(date)
                .andTrainCodeEqualTo(trainCode);
        //删某日除该车次车站数据
        daily_Train_StationMapper.deleteByExample(Daily_Train_StationExample);
        //查出某日该车次的车站数据
        List<Train_Station> trainStations = Train_StationService.selectByTrainCode(trainCode);
        if(CollUtil.isEmpty(trainStations)){
            LOG.info("该车次没有车站数据，日期【{}】车次【{}】的站点信息生成完毕", DateUtil.formatDate(date) , trainCode);
            return;
        }
        //生成该车次的数据
        for(Train_Station train :trainStations ){
            Daily_Train_Station daily_Train = BeanUtil.copyProperties(train, Daily_Train_Station.class);
            daily_Train.setId(SnowUtil.getSnowflakeNextId());
            daily_Train.setCreateTime(now);
            daily_Train.setUpdateTime(now);
            daily_Train.setDate(date);
            daily_Train_StationMapper.insert(daily_Train);
            LOG.info("生成日期【{}】车次【{}】的站点信息完成", DateUtil.formatDate(date), trainCode);
        }

    }
}
