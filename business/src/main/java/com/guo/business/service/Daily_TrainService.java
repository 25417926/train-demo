package com.guo.business.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.util.ObjectUtil;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.guo.common.resp.PageResp;
import com.guo.common.util.SnowUtil;
import com.guo.business.domain.Daily_Train;
import com.guo.business.domain.Daily_TrainExample;
import com.guo.business.mapper.Daily_TrainMapper;
import com.guo.business.req.Daily_TrainQueryReq;
import com.guo.business.req.Daily_TrainSaveReq;
import com.guo.business.resp.Daily_TrainQueryResp;
import javax.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class Daily_TrainService {

private static final Logger LOG = LoggerFactory.getLogger(Daily_TrainService.class);

@Resource
private Daily_TrainMapper daily_TrainMapper;

public void save(Daily_TrainSaveReq req) {
DateTime now = DateTime.now();
Daily_Train daily_Train = BeanUtil.copyProperties(req, Daily_Train.class);
if (ObjectUtil.isNull(daily_Train.getId())) {
daily_Train.setId(SnowUtil.getSnowflakeNextId());
daily_Train.setCreateTime(now);
daily_Train.setUpdateTime(now);
daily_TrainMapper.insert(daily_Train);
} else {
daily_Train.setUpdateTime(now);
daily_TrainMapper.updateByPrimaryKey(daily_Train);
}
}

public PageResp<Daily_TrainQueryResp> QueryList(Daily_TrainQueryReq req) {
    Daily_TrainExample daily_TrainExample = new Daily_TrainExample();
    daily_TrainExample.setOrderByClause("id desc");
    Daily_TrainExample.Criteria criteria = daily_TrainExample.createCriteria();

    LOG.info("查询页码：{}", req.getPage());
    LOG.info("每页条数：{}", req.getSize());
    PageHelper.startPage(req.getPage(), req.getSize());
    List<Daily_Train> daily_TrainList = daily_TrainMapper.selectByExample(daily_TrainExample);

    PageInfo<Daily_Train> pageInfo = new PageInfo<>(daily_TrainList);
    LOG.info("总行数：{}", pageInfo.getTotal());
    LOG.info("总页数：{}", pageInfo.getPages());

    List<Daily_TrainQueryResp> list = BeanUtil.copyToList(daily_TrainList, Daily_TrainQueryResp.class);

        PageResp<Daily_TrainQueryResp> pageResp = new PageResp<>();
            pageResp.setTotal(pageInfo.getTotal());
            pageResp.setList(list);
            return pageResp;
            }

            public void delete(Long id) {
            daily_TrainMapper.deleteByPrimaryKey(id);
            }
            }
