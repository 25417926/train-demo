package com.guo.business.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.util.ObjectUtil;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.guo.business.domain.Daily_Train_Station;
import com.guo.business.domain.Daily_Train_StationExample;
import com.guo.business.mapper.Daily_Train_StationMapper;
import com.guo.business.req.Daily_Train_StationQueryReq;
import com.guo.business.req.Daily_Train_StationSaveReq;
import com.guo.business.resp.Daily_Train_StationQueryResp;
import com.guo.common.resp.PageResp;
import com.guo.common.util.SnowUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

@Service
public class Daily_Train_StationService {

    private static final Logger LOG = LoggerFactory.getLogger(Daily_Train_StationService.class);

    @Resource
    private Daily_Train_StationMapper daily_Train_StationMapper;

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
        daily_Train_StationExample.setOrderByClause("id desc");
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
}
