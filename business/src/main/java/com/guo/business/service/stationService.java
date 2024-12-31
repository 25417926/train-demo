package com.guo.business.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.util.ObjectUtil;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.guo.business.domain.station;
import com.guo.business.domain.stationExample;
import com.guo.business.mapper.stationMapper;
import com.guo.business.req.stationQueryReq;
import com.guo.business.req.stationSaveReq;
import com.guo.business.resp.stationQueryResp;
import com.guo.common.resp.PageResp;
import com.guo.common.util.SnowUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

@Service
public class stationService {

    private static final Logger LOG = LoggerFactory.getLogger(stationService.class);

    @Resource
    private stationMapper stationMapper;

    public void save(stationSaveReq req) {
        DateTime now = DateTime.now();
        station station = BeanUtil.copyProperties(req, station.class);
        if (ObjectUtil.isNull(station.getId())) {
            station.setId(SnowUtil.getSnowflakeNextId());
            station.setCreateTime(now);
            station.setUpdateTime(now);
            stationMapper.insert(station);
        } else {
            station.setUpdateTime(now);
            stationMapper.updateByPrimaryKey(station);
        }
    }

    public PageResp<stationQueryResp> queryList(stationQueryReq req) {
        stationExample stationExample = new stationExample();
        stationExample.setOrderByClause("id desc");
        stationExample.Criteria criteria = stationExample.createCriteria();

        LOG.info("查询页码：{}", req.getPage());
        LOG.info("每页条数：{}", req.getSize());
        PageHelper.startPage(req.getPage(), req.getSize());
        List<station> stationList = stationMapper.selectByExample(stationExample);

        PageInfo<station> pageInfo = new PageInfo<>(stationList);
        LOG.info("总行数：{}", pageInfo.getTotal());
        LOG.info("总页数：{}", pageInfo.getPages());

        List<stationQueryResp> list = BeanUtil.copyToList(stationList, stationQueryResp.class);

        PageResp<stationQueryResp> pageResp = new PageResp<>();
        pageResp.setTotal(pageInfo.getTotal());
        pageResp.setList(list);
        return pageResp;
    }

    public void delete(Long id) {
        stationMapper.deleteByPrimaryKey(id);
    }
}
