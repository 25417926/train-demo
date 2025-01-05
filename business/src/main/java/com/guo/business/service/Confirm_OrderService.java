package com.guo.business.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSON;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.guo.business.TypeEnum.ConfirmOrderStatusEnum;
import com.guo.business.domain.Confirm_Order;
import com.guo.business.domain.Confirm_OrderExample;
import com.guo.business.domain.Daily_Train_Ticket;
import com.guo.business.mapper.Confirm_OrderMapper;
import com.guo.business.req.Confirm_OrderDoReq;
import com.guo.business.req.Confirm_OrderQueryReq;
import com.guo.business.req.Confirm_OrderSaveReq;
import com.guo.business.resp.Confirm_OrderQueryResp;
import com.guo.common.context.LoginMemberContext;
import com.guo.common.resp.PageResp;
import com.guo.common.util.SnowUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

@Service
public class Confirm_OrderService {

    private static final Logger LOG = LoggerFactory.getLogger(Confirm_OrderService.class);

    @Resource
    private Confirm_OrderMapper confirm_OrderMapper;
    @Resource
    private Daily_Train_TicketService daily_Train_TicketService;
    public void save(Confirm_OrderSaveReq req) {
        DateTime now = DateTime.now();
        Confirm_Order confirm_Order = BeanUtil.copyProperties(req, Confirm_Order.class);
        if (ObjectUtil.isNull(confirm_Order.getId())) {
            confirm_Order.setId(SnowUtil.getSnowflakeNextId());
            confirm_Order.setCreateTime(now);
            confirm_Order.setUpdateTime(now);
            confirm_OrderMapper.insert(confirm_Order);
        } else {
            confirm_Order.setUpdateTime(now);
            confirm_OrderMapper.updateByPrimaryKey(confirm_Order);
        }
    }

    public PageResp<Confirm_OrderQueryResp> QueryList(Confirm_OrderQueryReq req) {
        Confirm_OrderExample confirm_OrderExample = new Confirm_OrderExample();
        confirm_OrderExample.setOrderByClause("id desc");
        Confirm_OrderExample.Criteria criteria = confirm_OrderExample.createCriteria();

        LOG.info("查询页码：{}", req.getPage());
        LOG.info("每页条数：{}", req.getSize());
        PageHelper.startPage(req.getPage(), req.getSize());
        List<Confirm_Order> confirm_OrderList = confirm_OrderMapper.selectByExample(confirm_OrderExample);

        PageInfo<Confirm_Order> pageInfo = new PageInfo<>(confirm_OrderList);
        LOG.info("总行数：{}", pageInfo.getTotal());
        LOG.info("总页数：{}", pageInfo.getPages());

        List<Confirm_OrderQueryResp> list = BeanUtil.copyToList(confirm_OrderList, Confirm_OrderQueryResp.class);

        PageResp<Confirm_OrderQueryResp> pageResp = new PageResp<>();
        pageResp.setTotal(pageInfo.getTotal());
        pageResp.setList(list);
        return pageResp;
    }

    public void delete(Long id) {
        confirm_OrderMapper.deleteByPrimaryKey(id);
    }

    public void DoStorage(Confirm_OrderDoReq req) {
        //车次是否存在

        //保存确认订单表
        DateTime now = DateTime.now();
        Confirm_Order confirm_Order = new Confirm_Order();
        confirm_Order.setId(SnowUtil.getSnowflakeNextId());
        confirm_Order.setMemberId(LoginMemberContext.getId());
        Date date =req.getDate();
        String trainCode = req.getTrainCode();
        String start = req.getStart();
        String end = req.getEnd();
        confirm_Order.setDate(date);
        confirm_Order.setTrainCode(trainCode);
        confirm_Order.setStart(start);
        confirm_Order.setEnd(end);
        confirm_Order.setDailyTrainTicketId(req.getDailyTrainTicketId());
        confirm_Order.setStatus(ConfirmOrderStatusEnum.INIT.getCode());
        confirm_Order.setTickets(JSON.toJSONString(req.getTickets()));
        confirm_Order.setCreateTime(now);
        confirm_Order.setUpdateTime(now);
        confirm_OrderMapper.insert(confirm_Order);
        //查出余票数据，需要得到真实库存
        Daily_Train_Ticket dailyTrainTicket = daily_Train_TicketService.selectByUnique(date, trainCode, start, end);
        LOG.info("查出余票信息记录：{}", dailyTrainTicket);
        //
    }

}
