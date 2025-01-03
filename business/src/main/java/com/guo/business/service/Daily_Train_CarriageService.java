package com.guo.business.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.util.ObjectUtil;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.guo.common.resp.PageResp;
import com.guo.common.util.SnowUtil;
import com.guo.business.domain.Daily_Train_Carriage;
import com.guo.business.domain.Daily_Train_CarriageExample;
import com.guo.business.mapper.Daily_Train_CarriageMapper;
import com.guo.business.req.Daily_Train_CarriageQueryReq;
import com.guo.business.req.Daily_Train_CarriageSaveReq;
import com.guo.business.resp.Daily_Train_CarriageQueryResp;
import javax.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class Daily_Train_CarriageService {

private static final Logger LOG = LoggerFactory.getLogger(Daily_Train_CarriageService.class);

@Resource
private Daily_Train_CarriageMapper daily_Train_CarriageMapper;

public void save(Daily_Train_CarriageSaveReq req) {
DateTime now = DateTime.now();
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
    daily_Train_CarriageExample.setOrderByClause("id desc");
    Daily_Train_CarriageExample.Criteria criteria = daily_Train_CarriageExample.createCriteria();

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
            }
