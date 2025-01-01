package com.guo.business.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.util.ObjectUtil;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.guo.business.domain.Train_Station;
import com.guo.business.domain.Train_StationExample;
import com.guo.business.mapper.Train_StationMapper;
import com.guo.business.req.Train_StationQueryReq;
import com.guo.business.req.Train_StationSaveReq;
import com.guo.business.resp.Train_StationQueryResp;
import com.guo.common.resp.PageResp;
import com.guo.common.util.SnowUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

@Service
public class Train_StationService {

private static final Logger LOG = LoggerFactory.getLogger(Train_StationService.class);

@Resource
private Train_StationMapper train_StationMapper;

public void save(Train_StationSaveReq req) {
DateTime now = DateTime.now();
Train_Station train_Station = BeanUtil.copyProperties(req, Train_Station.class);
if (ObjectUtil.isNull(train_Station.getId())) {
train_Station.setId(SnowUtil.getSnowflakeNextId());
train_Station.setCreateTime(now);
train_Station.setUpdateTime(now);
train_StationMapper.insert(train_Station);
} else {
train_Station.setUpdateTime(now);
train_StationMapper.updateByPrimaryKey(train_Station);
}
}

public PageResp<Train_StationQueryResp> queryList(Train_StationQueryReq req) {
    Train_StationExample train_StationExample = new Train_StationExample();
    train_StationExample.setOrderByClause("id desc");
    Train_StationExample.Criteria criteria = train_StationExample.createCriteria();

    LOG.info("查询页码：{}", req.getPage());
    LOG.info("每页条数：{}", req.getSize());
    PageHelper.startPage(req.getPage(), req.getSize());
    List<Train_Station> train_StationList = train_StationMapper.selectByExample(train_StationExample);

    PageInfo<Train_Station> pageInfo = new PageInfo<>(train_StationList);
    LOG.info("总行数：{}", pageInfo.getTotal());
    LOG.info("总页数：{}", pageInfo.getPages());

    List<Train_StationQueryResp> list = BeanUtil.copyToList(train_StationList, Train_StationQueryResp.class);

        PageResp<Train_StationQueryResp> pageResp = new PageResp<>();
            pageResp.setTotal(pageInfo.getTotal());
            pageResp.setList(list);
            return pageResp;
            }

            public void delete(Long id) {
            train_StationMapper.deleteByPrimaryKey(id);
            }
            }
