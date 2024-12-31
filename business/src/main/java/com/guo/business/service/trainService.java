package com.guo.business.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.util.ObjectUtil;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.guo.business.TypeEnum.TarinTypeEnum;
import com.guo.business.domain.train;
import com.guo.business.domain.trainExample;
import com.guo.business.mapper.trainMapper;
import com.guo.business.req.trainQueryReq;
import com.guo.business.req.trainSaveReq;
import com.guo.business.resp.trainQueryResp;
import com.guo.common.resp.PageResp;
import com.guo.common.util.SnowUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class trainService {

    private static final Logger LOG = LoggerFactory.getLogger(trainService.class);

    @Resource
    private trainMapper trainMapper;

    public void save(trainSaveReq req) {
        DateTime now = DateTime.now();
        train train = BeanUtil.copyProperties(req, train.class);
        if (ObjectUtil.isNull(train.getId())) {
            train.setId(SnowUtil.getSnowflakeNextId());
            train.setCreateTime(now);
            train.setUpdateTime(now);
            trainMapper.insert(train);
        } else {
            train.setUpdateTime(now);
            trainMapper.updateByPrimaryKey(train);
        }
    }

    public PageResp<trainQueryResp> queryList(trainQueryReq req) {
        trainExample trainExample = new trainExample();
        trainExample.setOrderByClause("id desc");
        trainExample.Criteria criteria = trainExample.createCriteria();

        LOG.info("查询页码：{}", req.getPage());
        LOG.info("每页条数：{}", req.getSize());
        PageHelper.startPage(req.getPage(), req.getSize());
        List<train> trainList = trainMapper.selectByExample(trainExample);

        PageInfo<train> pageInfo = new PageInfo<>(trainList);
        LOG.info("总行数：{}", pageInfo.getTotal());
        LOG.info("总页数：{}", pageInfo.getPages());

        List<trainQueryResp> list = BeanUtil.copyToList(trainList, trainQueryResp.class).stream().map(
                resp->{
                    resp.setType(TarinTypeEnum.fromCode(resp.getType(),null).getDesc());
                    return resp;
                }
        ).collect(Collectors.toList());

        PageResp<trainQueryResp> pageResp = new PageResp<>();
        pageResp.setTotal(pageInfo.getTotal());
        pageResp.setList(list);
        return pageResp;
    }

    public void delete(Long id) {
        trainMapper.deleteByPrimaryKey(id);
    }
}
