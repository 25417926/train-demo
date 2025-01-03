package com.guo.business.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.util.ObjectUtil;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.guo.business.domain.Daily_Train_Seat;
import com.guo.business.domain.Daily_Train_SeatExample;
import com.guo.business.mapper.Daily_Train_SeatMapper;
import com.guo.business.req.Daily_Train_SeatQueryReq;
import com.guo.business.req.Daily_Train_SeatSaveReq;
import com.guo.business.resp.Daily_Train_SeatQueryResp;
import com.guo.common.resp.PageResp;
import com.guo.common.util.SnowUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

@Service
public class Daily_Train_SeatService {

    private static final Logger LOG = LoggerFactory.getLogger(Daily_Train_SeatService.class);

    @Resource
    private Daily_Train_SeatMapper daily_Train_SeatMapper;

    public void save(Daily_Train_SeatSaveReq req) {
        DateTime now = DateTime.now();
        Daily_Train_Seat daily_Train_Seat = BeanUtil.copyProperties(req, Daily_Train_Seat.class);
        if (ObjectUtil.isNull(daily_Train_Seat.getId())) {
            daily_Train_Seat.setId(SnowUtil.getSnowflakeNextId());
            daily_Train_Seat.setCreateTime(now);
            daily_Train_Seat.setUpdateTime(now);
            daily_Train_SeatMapper.insert(daily_Train_Seat);
        } else {
            daily_Train_Seat.setUpdateTime(now);
            daily_Train_SeatMapper.updateByPrimaryKey(daily_Train_Seat);
        }
    }

    public PageResp<Daily_Train_SeatQueryResp> QueryList(Daily_Train_SeatQueryReq req) {
        Daily_Train_SeatExample daily_Train_SeatExample = new Daily_Train_SeatExample();
        daily_Train_SeatExample.setOrderByClause("train_code asc,`carriage_index` asc,`carriage_seat_index` asc");
        Daily_Train_SeatExample.Criteria criteria = daily_Train_SeatExample.createCriteria();
        if(ObjectUtil.isNotNull(req.getTrainCode())) {
            criteria.andTrainCodeEqualTo(req.getTrainCode());
        }
        LOG.info("查询页码：{}", req.getPage());
        LOG.info("每页条数：{}", req.getSize());
        PageHelper.startPage(req.getPage(), req.getSize());
        List<Daily_Train_Seat> daily_Train_SeatList = daily_Train_SeatMapper.selectByExample(daily_Train_SeatExample);

        PageInfo<Daily_Train_Seat> pageInfo = new PageInfo<>(daily_Train_SeatList);
        LOG.info("总行数：{}", pageInfo.getTotal());
        LOG.info("总页数：{}", pageInfo.getPages());

        List<Daily_Train_SeatQueryResp> list = BeanUtil.copyToList(daily_Train_SeatList, Daily_Train_SeatQueryResp.class);

        PageResp<Daily_Train_SeatQueryResp> pageResp = new PageResp<>();
        pageResp.setTotal(pageInfo.getTotal());
        pageResp.setList(list);
        return pageResp;
    }

    public void delete(Long id) {
        daily_Train_SeatMapper.deleteByPrimaryKey(id);
    }
}
