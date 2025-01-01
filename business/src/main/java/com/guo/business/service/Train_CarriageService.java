package com.guo.business.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.util.ObjectUtil;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.guo.business.TypeEnum.TarinTypeEnum;
import com.guo.business.domain.Train_Carriage;
import com.guo.business.domain.Train_CarriageExample;
import com.guo.business.mapper.Train_CarriageMapper;
import com.guo.business.req.Train_CarriageQueryReq;
import com.guo.business.req.Train_CarriageSaveReq;
import com.guo.business.resp.Train_CarriageQueryResp;
import com.guo.common.resp.PageResp;
import com.guo.common.util.SnowUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class Train_CarriageService {

    private static final Logger LOG = LoggerFactory.getLogger(Train_CarriageService.class);

    @Resource
    private Train_CarriageMapper train_CarriageMapper;

    public void save(Train_CarriageSaveReq req) {
        DateTime now = DateTime.now();
        Train_Carriage train_Carriage = BeanUtil.copyProperties(req, Train_Carriage.class);
        if (ObjectUtil.isNull(train_Carriage.getId())) {
            train_Carriage.setId(SnowUtil.getSnowflakeNextId());
            train_Carriage.setCreateTime(now);
            train_Carriage.setUpdateTime(now);
            train_CarriageMapper.insert(train_Carriage);
        } else {
            train_Carriage.setUpdateTime(now);
            train_CarriageMapper.updateByPrimaryKey(train_Carriage);
        }
    }

    public PageResp<Train_CarriageQueryResp> QueryList(Train_CarriageQueryReq req) {
        Train_CarriageExample train_CarriageExample = new Train_CarriageExample();
        train_CarriageExample.setOrderByClause("id desc");
        Train_CarriageExample.Criteria criteria = train_CarriageExample.createCriteria();

        LOG.info("查询页码：{}", req.getPage());
        LOG.info("每页条数：{}", req.getSize());
        PageHelper.startPage(req.getPage(), req.getSize());
        List<Train_Carriage> train_CarriageList = train_CarriageMapper.selectByExample(train_CarriageExample);

        PageInfo<Train_Carriage> pageInfo = new PageInfo<>(train_CarriageList);
        LOG.info("总行数：{}", pageInfo.getTotal());
        LOG.info("总页数：{}", pageInfo.getPages());

        List<Train_CarriageQueryResp> list = BeanUtil.copyToList(train_CarriageList, Train_CarriageQueryResp.class).stream().map(
                resp -> {
                    resp.setSeatType(TarinTypeEnum.fromCode(resp.getSeatType(), null).getDesc());
                    return resp;
                }
        ).collect(Collectors.toList());

        PageResp<Train_CarriageQueryResp> pageResp = new PageResp<>();
        pageResp.setTotal(pageInfo.getTotal());
        pageResp.setList(list);
        return pageResp;
    }

    public void delete(Long id) {
        train_CarriageMapper.deleteByPrimaryKey(id);
    }
}
